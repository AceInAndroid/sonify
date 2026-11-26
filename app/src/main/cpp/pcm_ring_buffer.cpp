#include "pcm_ring_buffer.h"

#include <algorithm>
#include <cmath>
#include <cstring>

PcmRingBufferSource::PcmRingBufferSource(int bufferMs, bool fadeEnabled)
        : ring_(kCapBytes, 0),
          bufferMs_(std::clamp(bufferMs, 60, 500)),
          fadeEnabled_(fadeEnabled) {}

void PcmRingBufferSource::onFormat(uint32_t sampleRate, int channels, int subslotBytes) {
    rate_       = sampleRate > 0 ? sampleRate : 44100;
    channels_   = channels > 0 ? channels : 2;
    subslot_    = (subslotBytes >= 2 && subslotBytes <= 4) ? subslotBytes : 4;
    frameBytes_ = channels_ * subslot_;

    // 按目标采样率计算软水位上限（保证整帧对齐，且不超过物理容量的一半以上）
    size_t waterFrames = (size_t)((uint64_t)rate_ * (uint64_t)bufferMs_ / 1000u);
    size_t waterBytes  = waterFrames * (size_t)frameBytes_;
    const size_t hardMax = kCapBytes - (size_t)frameBytes_ * 16u;
    if (waterBytes > hardMax) waterBytes = hardMax;
    if (waterBytes < (size_t)frameBytes_ * 256u) waterBytes = (size_t)frameBytes_ * 256u;
    maxWaterBytes_ = waterBytes;

    // 5ms 防爆音淡入淡出
    fadeFrames_ = (int)(rate_ / 200u);
    if (fadeFrames_ < 32) fadeFrames_ = 32;
    env_ = fadeEnabled_ ? 0.0 : 1.0;

    LOGI("PcmRingBufferSource 格式: %u Hz / %d ch / %dB subslot, 水位=%dms (%zu B), 淡变=%d帧",
         rate_, channels_, subslot_, bufferMs_, maxWaterBytes_, fadeFrames_);
}

int PcmRingBufferSource::inputSampleBytes(int pcmEncoding) {
    switch (pcmEncoding) {
        case PCM_ENC_16BIT:    return 2;
        case PCM_ENC_24BIT:
        case PCM_ENC_24BIT_BE: return 3;
        case PCM_ENC_32BIT:
        case PCM_ENC_32BIT_BE:
        case PCM_ENC_FLOAT:    return 4;
        default:               return 0;
    }
}

// 全部高位对齐至 signed 32-bit 容器（最高有效位在 bit31），不损失 24-bit 精度
int32_t PcmRingBufferSource::readSampleAsInt32(const uint8_t *p, int pcmEncoding) {
    switch (pcmEncoding) {
        case PCM_ENC_16BIT: {
            uint32_t u = ((uint32_t)p[0] << 16) | ((uint32_t)p[1] << 24);
            return (int32_t)u;
        }
        case PCM_ENC_24BIT: {
            uint32_t u = ((uint32_t)p[0] << 8) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 24);
            return (int32_t)u;
        }
        case PCM_ENC_24BIT_BE: {
            uint32_t u = ((uint32_t)p[2] << 8) | ((uint32_t)p[1] << 16) | ((uint32_t)p[0] << 24);
            return (int32_t)u;
        }
        case PCM_ENC_32BIT: {
            uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
            return (int32_t)u;
        }
        case PCM_ENC_32BIT_BE: {
            uint32_t u = (uint32_t)p[3] | ((uint32_t)p[2] << 8) |
                         ((uint32_t)p[1] << 16) | ((uint32_t)p[0] << 24);
            return (int32_t)u;
        }
        case PCM_ENC_FLOAT: {
            float f = 0.0f;
            std::memcpy(&f, p, sizeof(float));
            if (f > 1.0f) f = 1.0f;
            else if (f < -1.0f) f = -1.0f;
            double scaled = (double)f * 2147483647.0;
            return (int32_t)std::llround(scaled);
        }
        default:
            return 0;
    }
}

void PcmRingBufferSource::writeInt32ToSubslot(uint8_t *dst, int32_t s, int subslotBytes) {
    uint32_t u = (uint32_t)s;
    switch (subslotBytes) {
        case 2:
            dst[0] = (uint8_t)((u >> 16) & 0xFF);
            dst[1] = (uint8_t)((u >> 24) & 0xFF);
            break;
        case 3:
            dst[0] = (uint8_t)((u >> 8) & 0xFF);
            dst[1] = (uint8_t)((u >> 16) & 0xFF);
            dst[2] = (uint8_t)((u >> 24) & 0xFF);
            break;
        default:  // 4 bytes
            dst[0] = (uint8_t)(u & 0xFF);
            dst[1] = (uint8_t)((u >> 8) & 0xFF);
            dst[2] = (uint8_t)((u >> 16) & 0xFF);
            dst[3] = (uint8_t)((u >> 24) & 0xFF);
            break;
    }
}

void PcmRingBufferSource::scaleSampleInPlace(uint8_t *p, int subslotBytes, double gain) {
    int32_t s = 0;
    switch (subslotBytes) {
        case 2:
            s = readSampleAsInt32(p, PCM_ENC_16BIT);
            break;
        case 3:
            s = readSampleAsInt32(p, PCM_ENC_24BIT);
            break;
        default:
            s = readSampleAsInt32(p, PCM_ENC_32BIT);
            break;
    }
    double v = (double)s * gain;
    if (v > 2147483647.0) v = 2147483647.0;
    if (v < -2147483648.0) v = -2147483648.0;
    writeInt32ToSubslot(p, (int32_t)std::llround(v), subslotBytes);
}

size_t PcmRingBufferSource::write(const uint8_t *src, size_t bytes,
                                  int pcmEncoding, int srcChannels) {
    if (!src || bytes == 0 || frameBytes_ <= 0) return 0;
    const int inSampleBytes = inputSampleBytes(pcmEncoding);
    if (inSampleBytes <= 0 || srcChannels <= 0) return 0;

    const size_t inFrameBytes = (size_t)(inSampleBytes * srcChannels);
    const size_t inFrames = bytes / inFrameBytes;
    if (inFrames == 0) return 0;

    const uint64_t w = writePos_.load(std::memory_order_relaxed);
    const uint64_t r = readPos_.load(std::memory_order_acquire);
    const uint64_t ft = flushTarget_.load(std::memory_order_relaxed);
    const uint64_t effectiveRead = (ft > r) ? ft : r;

    const uint64_t used = (w >= effectiveRead) ? (w - effectiveRead) : 0;
    if (used >= maxWaterBytes_) return 0;

    const size_t freeBytes = (size_t)(maxWaterBytes_ - used);
    const size_t maxFrames = freeBytes / (size_t)frameBytes_;
    const size_t framesToWrite = std::min(inFrames, maxFrames);
    if (framesToWrite == 0) return 0;

    const float maxG = maxGain_.load(std::memory_order_relaxed);
    const float vol = std::min(volume_.load(std::memory_order_relaxed), maxG);
    const bool bitPerfect = (vol >= 0.9999f && vol <= 1.0001f);

    // 使用栈上分块临时缓冲，避免大拷贝并确保环绕写入高效
    uint8_t chunk[2048];
    const size_t chunkMaxFrames = sizeof(chunk) / (size_t)frameBytes_;

    uint64_t curW = w;
    size_t doneFrames = 0;

    while (doneFrames < framesToWrite) {
        const size_t batch = std::min(framesToWrite - doneFrames, chunkMaxFrames);
        uint8_t *out = chunk;

        for (size_t f = 0; f < batch; f++) {
            const uint8_t *inFrame = src + (doneFrames + f) * inFrameBytes;
            for (int c = 0; c < channels_; c++) {
                // 声道映射：单声道自动复制到双声道，多余声道按索引取
                const int sc = (c < srcChannels) ? c : (srcChannels - 1);
                const uint8_t *sp = inFrame + (size_t)sc * (size_t)inSampleBytes;

                int32_t s32 = readSampleAsInt32(sp, pcmEncoding);
                if (!bitPerfect) {
                    double v = (double)s32 * (double)vol;
                    if (v > 2147483647.0) v = 2147483647.0;
                    if (v < -2147483648.0) v = -2147483648.0;
                    s32 = (int32_t)std::llround(v);
                }
                writeInt32ToSubslot(out, s32, subslot_);
                out += subslot_;
            }
        }

        const size_t outBytes = batch * (size_t)frameBytes_;
        const size_t idx = (size_t)(curW & kCapMask);
        const size_t firstPart = std::min(outBytes, kCapBytes - idx);
        std::memcpy(ring_.data() + idx, chunk, firstPart);
        if (firstPart < outBytes) {
            std::memcpy(ring_.data(), chunk + firstPart, outBytes - firstPart);
        }

        curW += outBytes;
        doneFrames += batch;
    }

    eos_.store(false, std::memory_order_relaxed);
    writePos_.store(curW, std::memory_order_release);
    return framesToWrite * inFrameBytes;
}

size_t PcmRingBufferSource::read(uint8_t *dst, size_t bytes) {
    if (!dst || bytes == 0 || frameBytes_ <= 0) return 0;

    uint64_t r = readPos_.load(std::memory_order_relaxed);
    const uint64_t ft = flushTarget_.load(std::memory_order_acquire);
    if (ft > r) {
        r = ft;
        readPos_.store(r, std::memory_order_release);
        if (fadeEnabled_) env_ = 0.0;
    }

    const size_t reqFrames = bytes / (size_t)frameBytes_;
    const bool paused = paused_.load(std::memory_order_acquire);

    // 暂停态：如果包络已归零则直接输出静音，不消费缓冲与帧计数
    if (paused && (!fadeEnabled_ || env_ <= 0.0001)) {
        std::memset(dst, 0, bytes);
        return bytes;
    }

    const uint64_t w = writePos_.load(std::memory_order_acquire);
    const uint64_t availBytes = (w >= r) ? (w - r) : 0;
    const size_t availFrames = (size_t)(availBytes / (uint64_t)frameBytes_);

    const size_t takeFrames = std::min(reqFrames, availFrames);
    const size_t takeBytes  = takeFrames * (size_t)frameBytes_;

    if (takeBytes > 0) {
        const size_t idx = (size_t)(r & kCapMask);
        const size_t firstPart = std::min(takeBytes, kCapBytes - idx);
        std::memcpy(dst, ring_.data() + idx, firstPart);
        if (firstPart < takeBytes) {
            std::memcpy(dst + firstPart, ring_.data(), takeBytes - firstPart);
        }
        r += takeBytes;
        readPos_.store(r, std::memory_order_release);
        consumedFrames_.fetch_add(takeFrames, std::memory_order_release);

        // 仅在需要淡入或淡出时才修改样本；稳态 env_ == 1.0 完全不触碰 PCM，保证 bit-perfect
        if (fadeEnabled_) {
            const double step = 1.0 / (double)fadeFrames_;
            if (paused) {
                for (size_t f = 0; f < takeFrames; f++) {
                    env_ = std::max(0.0, env_ - step);
                    uint8_t *fp = dst + f * (size_t)frameBytes_;
                    for (int c = 0; c < channels_; c++) {
                        scaleSampleInPlace(fp + c * subslot_, subslot_, env_);
                    }
                }
            } else if (env_ < 0.9999) {
                for (size_t f = 0; f < takeFrames; f++) {
                    env_ = std::min(1.0, env_ + step);
                    uint8_t *fp = dst + f * (size_t)frameBytes_;
                    for (int c = 0; c < channels_; c++) {
                        scaleSampleInPlace(fp + c * subslot_, subslot_, env_);
                    }
                    if (env_ >= 0.9999) {
                        env_ = 1.0;
                        break;
                    }
                }
            }
        }
    }

    if (takeBytes < bytes) {
        std::memset(dst + takeBytes, 0, bytes - takeBytes);
        if (!paused && !eos_.load(std::memory_order_acquire)) {
            ringUnderruns_.fetch_add(1, std::memory_order_relaxed);
            if (fadeEnabled_) env_ = 0.0;   // 下次恢复数据时做短淡入，避免咔哒声
        } else if (paused) {
            env_ = 0.0;
        }
    }
    return bytes;
}

void PcmRingBufferSource::flush() {
    const uint64_t w = writePos_.load(std::memory_order_relaxed);
    flushTarget_.store(w, std::memory_order_release);
    consumedFrames_.store(0, std::memory_order_release);
    eos_.store(false, std::memory_order_release);
}

void PcmRingBufferSource::setPaused(bool paused) {
    paused_.store(paused, std::memory_order_release);
}

void PcmRingBufferSource::setVolume(float volume) {
    if (volume < 0.0f) volume = 0.0f;
    if (volume > 1.0f) volume = 1.0f;
    const float maxG = maxGain_.load(std::memory_order_relaxed);
    if (volume > maxG) volume = maxG;
    volume_.store(volume, std::memory_order_relaxed);
}

void PcmRingBufferSource::setVolumeDb(double db, bool mute, double maxDb) {
    if (maxDb > 0.0) maxDb = 0.0;
    if (maxDb < -96.0) maxDb = -96.0;
    const float maxG = (maxDb >= -0.01) ? 1.0f : (float)std::pow(10.0, maxDb / 20.0);
    maxGain_.store(maxG, std::memory_order_relaxed);

    if (mute) {
        volume_.store(0.0f, std::memory_order_relaxed);
        return;
    }
    if (db > maxDb) db = maxDb;
    const float gain = (db >= -0.01) ? 1.0f : (float)std::pow(10.0, db / 20.0);
    volume_.store(std::min(gain, maxG), std::memory_order_relaxed);
}

void PcmRingBufferSource::setEos() {
    eos_.store(true, std::memory_order_release);
}

uint64_t PcmRingBufferSource::bufferedFrames() const {
    if (frameBytes_ <= 0) return 0;
    const uint64_t w = writePos_.load(std::memory_order_acquire);
    const uint64_t r = readPos_.load(std::memory_order_acquire);
    const uint64_t ft = flushTarget_.load(std::memory_order_acquire);
    const uint64_t effR = (ft > r) ? ft : r;
    if (w <= effR) return 0;
    return (w - effR) / (uint64_t)frameBytes_;
}

double PcmRingBufferSource::bufferedMs() const {
    if (rate_ == 0) return 0.0;
    return (double)bufferedFrames() * 1000.0 / (double)rate_;
}

bool PcmRingBufferSource::isDrained() const {
    return eos_.load(std::memory_order_acquire) && bufferedFrames() == 0;
}
