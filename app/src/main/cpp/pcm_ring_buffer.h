// 无锁 SPSC PCM 环形缓冲音源：连接 Media3 ExoPlayer AudioSink 与 UacStream
//
// 设计原则：
//   - 音频回调线程（UacStream::fillTransfer → read）完全无锁、无动态分配
//   - volume == 1.0 且淡入完成时走纯整数移位路径，保证 100% bit-perfect
//   - 预留 M3 PEQ（float32 biquad）插入点
#pragma once

#include "uac_stream.h"

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

// 输入 PCM 编码枚举，与 Kotlin UsbAudioNative 中常量严格对应
enum PcmEncoding : int {
    PCM_ENC_16BIT    = 1,   // 2B little-endian signed
    PCM_ENC_24BIT    = 2,   // 3B packed little-endian signed
    PCM_ENC_32BIT    = 3,   // 4B little-endian signed
    PCM_ENC_FLOAT    = 4,   // 4B IEEE 754 float32 [-1.0, 1.0]
    PCM_ENC_24BIT_BE = 5,   // 3B packed big-endian signed
    PCM_ENC_32BIT_BE = 6,   // 4B big-endian signed
};

class PcmRingBufferSource : public AudioSource {
public:
    // bufferMs: 软水位上限（毫秒），用于控制背压与 seek/pause 响应延迟
    explicit PcmRingBufferSource(int bufferMs = 180, bool fadeEnabled = true);
    ~PcmRingBufferSource() override = default;

    // UacStream 启动时通知目标 DAC 格式
    void onFormat(uint32_t sampleRate, int channels, int subslotBytes) override;

    // USB iso 回调线程（唯一消费者）调用
    size_t read(uint8_t *dst, size_t bytes) override;

    // ExoPlayer 渲染线程（唯一生产者）调用：返回实际消费的输入字节数（按整帧对齐）
    size_t write(const uint8_t *src, size_t bytes, int pcmEncoding, int srcChannels);

    // 清空缓冲（seek / 切歌），无锁通知消费者推进读指针
    void flush();

    // 暂停 / 恢复（带平滑淡出 / 淡入防爆音）
    void setPaused(bool paused);

    // 软件音量（1.0f = bit-perfect 直通，不做浮点缩放；始终受 maxGain_ 硬钳位）
    void setVolume(float volume);

    // 对数 dB 软件音量 + 底层护耳硬上限钳位
    void setVolumeDb(double db, bool mute, double maxDb);

    // 标记输入流已结束，待缓冲播空后 isDrained() 返回 true
    void setEos();

    // 状态查询（供时钟对齐与实时诊断）
    uint64_t consumedFrames() const { return consumedFrames_.load(std::memory_order_acquire); }
    uint64_t ringUnderruns() const { return ringUnderruns_.load(std::memory_order_relaxed); }
    uint64_t bufferedFrames() const;
    double   bufferedMs() const;
    bool     isDrained() const;
    bool     isPaused() const { return paused_.load(std::memory_order_acquire); }

private:
    static constexpr size_t kCapBytes = 2u * 1024u * 1024u;  // 2MB (2 的幂)
    static constexpr size_t kCapMask  = kCapBytes - 1;

    static int inputSampleBytes(int pcmEncoding);
    static int32_t readSampleAsInt32(const uint8_t *p, int pcmEncoding);
    static void writeInt32ToSubslot(uint8_t *dst, int32_t s, int subslotBytes);
    static void scaleSampleInPlace(uint8_t *p, int subslotBytes, double gain);

    std::vector<uint8_t> ring_;

    uint32_t rate_        = 44100;
    int      channels_    = 2;
    int      subslot_     = 4;
    int      frameBytes_  = 8;
    int      bufferMs_    = 180;
    size_t   maxWaterBytes_ = 65536;
    bool     fadeEnabled_ = true;
    int      fadeFrames_  = 220;   // ~5ms

    // 单调递增的 64 位字节游标，取索引时用 & kCapMask
    // writePos_ 只由生产者写；readPos_ 只由消费者写；flushTarget_ 由生产者写、消费者消费
    std::atomic<uint64_t> writePos_{0};
    std::atomic<uint64_t> readPos_{0};
    std::atomic<uint64_t> flushTarget_{0};

    std::atomic<bool>     paused_{false};
    std::atomic<bool>     eos_{false};
    std::atomic<float>    volume_{1.0f};
    std::atomic<float>    maxGain_{1.0f};

    std::atomic<uint64_t> consumedFrames_{0};
    std::atomic<uint64_t> ringUnderruns_{0};

    // 仅由消费者线程访问的包络状态（0.0 = 静音, 1.0 = 全幅直通）
    double env_ = 0.0;
};
