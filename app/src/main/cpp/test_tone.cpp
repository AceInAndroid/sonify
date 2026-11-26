// 测试音发生器：M1 阶段唯一的"音源"，用来验证从 fd 到 DAC 的整条通路
#include "uac_stream.h"

#include <cmath>
#include <cstring>

class TestToneSource : public AudioSource {
public:
    TestToneSource(double freqHz, int durationSec, double amplitude)
            : freq_(freqHz), durationSec_(durationSec), amplitude_(amplitude) {}

    void onFormat(uint32_t sampleRate, int channels, int subslotBytes) override {
        rate_     = sampleRate;
        channels_ = channels;
        subslot_  = subslotBytes;
        phase_    = 0.0;
        // 相位增量：每个采样转过多少圈
        phaseInc_ = freq_ / (double)sampleRate;
        // 50ms 淡入，避免第一个采样是满幅造成的爆音
        fadeSamples_ = (uint32_t)(sampleRate * 0.05);
    }

    size_t read(uint8_t *dst, size_t bytes) override {
        if (rate_ == 0 || channels_ <= 0) {
            std::memset(dst, 0, bytes);
            return bytes;
        }

        const int frameBytes = channels_ * subslot_;
        const size_t frames  = bytes / (size_t)frameBytes;
        size_t out = 0;

        for (size_t f = 0; f < frames; f++) {
            // 时长到了就静音，但流继续（保持 DAC 时钟锁定，便于观察）
            double env = 1.0;
            if (durationSec_ > 0 && totalFrames_ >= (uint64_t)rate_ * durationSec_) {
                env = 0.0;
            } else if (totalFrames_ < fadeSamples_) {
                env = (double)totalFrames_ / (double)fadeSamples_;
            } else if (durationSec_ > 0) {
                uint64_t remain = (uint64_t)rate_ * durationSec_ - totalFrames_;
                if (remain < fadeSamples_) env = (double)remain / (double)fadeSamples_;
            }

            // 振幅由调用方给（0.5 = -6dBFS 已经很响了，DAC 硬件满音量时要小得多）
            double v = std::sin(phase_ * 2.0 * M_PI) * amplitude_ * env;

            for (int c = 0; c < channels_; c++) {
                write_sample(dst + out, v);
                out += (size_t)subslot_;
            }

            phase_ += phaseInc_;
            if (phase_ >= 1.0) phase_ -= 1.0;
            totalFrames_++;
        }

        // 防御：如果 bytes 不是整帧，剩余部分清零
        if (out < bytes) {
            std::memset(dst + out, 0, bytes - out);
            out = bytes;
        }
        return bytes;
    }

private:
    void write_sample(uint8_t *p, double v) {
        if (v > 1.0) v = 1.0;
        if (v < -1.0) v = -1.0;

        switch (subslot_) {
            case 2: {   // 16-bit
                int16_t s = (int16_t)std::lround(v * 32767.0);
                p[0] = (uint8_t)(s & 0xFF);
                p[1] = (uint8_t)((s >> 8) & 0xFF);
                break;
            }
            case 3: {   // 24-bit packed
                int32_t s = (int32_t)std::lround(v * 8388607.0);
                p[0] = (uint8_t)(s & 0xFF);
                p[1] = (uint8_t)((s >> 8) & 0xFF);
                p[2] = (uint8_t)((s >> 16) & 0xFF);
                break;
            }
            default: {  // 32-bit 容器（24 或 32 bit 有效位都靠左对齐）
                int32_t s = (int32_t)std::lround(v * 2147483647.0);
                p[0] = (uint8_t)(s & 0xFF);
                p[1] = (uint8_t)((s >> 8) & 0xFF);
                p[2] = (uint8_t)((s >> 16) & 0xFF);
                p[3] = (uint8_t)((s >> 24) & 0xFF);
                break;
            }
        }
    }

    double   freq_        = 440.0;
    int      durationSec_ = 0;
    double   amplitude_   = 0.08;
    uint32_t rate_        = 0;
    int      channels_    = 2;
    int      subslot_     = 3;
    double   phase_       = 0.0;
    double   phaseInc_    = 0.0;
    uint32_t fadeSamples_ = 1;
    uint64_t totalFrames_ = 0;
};

// 工厂函数，给 JNI 层用
AudioSource *create_test_tone(double freqHz, int durationSec, double amplitude) {
    if (amplitude <= 0.0) amplitude = 0.001;
    if (amplitude > 1.0)  amplitude = 1.0;
    return new TestToneSource(freqHz, durationSec, amplitude);
}
