// UAC 流控制：采样率协商 + alt setting 切换 + isochronous OUT
#pragma once

#include "uac_desc.h"

#include <atomic>
#include <cstdint>
#include <mutex>
#include <thread>
#include <vector>

// 数据源抽象：测试音、解码器输出、DSP 输出都实现它
class AudioSource {
public:
    virtual ~AudioSource() = default;
    // 填充 dst 最多 bytes 字节，返回实际写入的字节数。
    // 返回 0 表示流结束（调用方会用静音补齐并停止）。
    virtual size_t read(uint8_t *dst, size_t bytes) = 0;
    // 目标格式变化时会被调用（例如 DSP 改了位深）
    virtual void onFormat(uint32_t sampleRate, int channels, int subslotBytes) {}
};

// 设置采样率。UAC2 走 Clock Source，UAC1 走 endpoint。
bool uac_set_sample_rate(libusb_device_handle *h, const UacDeviceInfo &info,
                         const StreamAlt &alt, uint32_t rate);

// 切换到某个 alt setting
bool uac_set_alt(libusb_device_handle *h, int interfaceNumber, int altSetting);

// 取值合法性检查
bool uac_supports_rate(const StreamAlt &alt, uint32_t rate);
// 找一个 >= rate 的最近可用值，找不到就返回原值
uint32_t uac_nearest_rate(const UacDeviceInfo &info, uint32_t rate);

class UacStream {
public:
    UacStream() = default;
    ~UacStream();

    // 必须先 uac_set_alt + uac_set_sample_rate，再 start
    // ctx 必须显式传入（libusb 1.0.27 没有 libusb_get_context）
    bool start(libusb_context *ctx, libusb_device_handle *handle, const StreamAlt &alt,
               uint32_t sampleRate, int subslotBytes);

    void stop();

    bool running() const { return running_.load(); }
    uint64_t underruns() const { return underruns_.load(); }
    uint64_t bytesSent() const { return bytesSent_.load(); }

    void setSource(AudioSource *src) { source_ = src; }

    // 已播放的采样数（用于进度条 / 时钟对齐）
    uint64_t samplesSent() const { return samplesSent_.load(); }

    // feedback 端点读到的原始值（UAC2 是 4 字节 16.16 定点）
    uint32_t feedbackRaw() const { return feedbackRaw_.load(); }
    // 由 feedback 反推出的设备实际采样率
    double feedbackRate() const { return feedbackRate_.load(); }
    uint64_t feedbackCount() const { return feedbackCount_.load(); }

    // feedback 闭环微调开关与当前生效速率
    void setFeedbackControlEnabled(bool enabled) { feedbackControlEnabled_.store(enabled); }
    double effectiveRate() const { return effectiveRate_.load(); }

    // 管道中在飞 URB 对应的音频帧数（用于进度条扣除 USB 管线延迟）
    uint64_t inFlightFrames() const {
        if (rate_ == 0) return 0;
        return (uint64_t)((double)kNumUrbs * packetsPerUrb_ * intervalUs_ * rate_ / 1000000.0);
    }

private:
    static constexpr int kNumUrbs = 4;

    static void LIBUSB_CALL onTransfer(libusb_transfer *t);
    void handleComplete(libusb_transfer *t);
    void fillTransfer(libusb_transfer *t);
    void eventLoop();

    // 异步反馈：设备通过 feedback 端点告诉主机每个 microframe 该发多少采样。
    // 这是 UAC2 async 模式的核心，不读它就只能靠固定速率硬发，会漂移。
    bool startFeedback();
    void stopFeedback();
    static void LIBUSB_CALL onFeedback(libusb_transfer *t);

    libusb_device_handle *handle_ = nullptr;
    libusb_context       *ctx_    = nullptr;
    StreamAlt             alt_{};
    uint32_t              rate_   = 0;
    int                   subslot_ = 3;
    int                   frameBytes_ = 6;   // channels * subslot

    // high-speed 下 iso 包是 125µs 一个，full-speed 下是 1ms。
    // 搞错的话发出去的数据量会差 8 倍，声音变成快放。
    int                   intervalUs_    = 1000;
    int                   packetsPerUrb_ = 8;

    libusb_transfer      *xfer_[kNumUrbs] = {nullptr};
    std::vector<uint8_t>  buffer_[kNumUrbs];

    AudioSource          *source_ = nullptr;
    std::atomic<bool>     running_{false};
    std::atomic<int>      inFlight_{0};
    std::atomic<uint64_t> underruns_{0};
    std::atomic<uint64_t> bytesSent_{0};
    std::atomic<uint64_t> samplesSent_{0};

    // 分数采样累加器：44100Hz、125µs 每包 → 每包 5.5125 个采样，交替 5/6
    double                fracAcc_ = 0.0;
    std::thread           eventThread_;
    std::mutex            submitMutex_;

    libusb_transfer      *fbXfer_ = nullptr;
    uint8_t               fbBuf_[8] = {0};
    std::atomic<uint32_t> feedbackRaw_{0};
    std::atomic<double>   feedbackRate_{0.0};
    std::atomic<uint64_t> feedbackCount_{0};
    std::atomic<bool>     feedbackControlEnabled_{true};
    std::atomic<double>   effectiveRate_{0.0};
};
