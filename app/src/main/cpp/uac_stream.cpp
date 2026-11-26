#include "uac_stream.h"

#include <algorithm>
#include <cstring>
#include <sys/time.h>

// ---------------------------------------------------------------------------
// 采样率协商
// ---------------------------------------------------------------------------

bool uac_supports_rate(const StreamAlt &alt, uint32_t rate) {
    if (alt.fmt.continuous) return rate >= alt.fmt.minRate && rate <= alt.fmt.maxRate;
    if (alt.fmt.sampleRates.empty()) return true;   // 未知，交给 SET_CUR 去试
    return std::find(alt.fmt.sampleRates.begin(), alt.fmt.sampleRates.end(), rate) !=
           alt.fmt.sampleRates.end();
}

uint32_t uac_nearest_rate(const UacDeviceInfo &info, uint32_t rate) {
    uint32_t best = 0;
    uint32_t bestDiff = UINT32_MAX;
    for (const auto &a : info.alts) {
        for (uint32_t r : a.fmt.sampleRates) {
            uint32_t d = (r > rate) ? (r - rate) : (rate - r);
            if (d < bestDiff) { bestDiff = d; best = r; }
        }
        if (a.fmt.continuous && rate >= a.fmt.minRate && rate <= a.fmt.maxRate) return rate;
    }
    return best ? best : rate;
}

bool uac_set_sample_rate(libusb_device_handle *h, const UacDeviceInfo &info,
                         const StreamAlt &alt, uint32_t rate) {
    if (info.uacVersion >= 2) {
        if (!info.hasClockSource) {
            LOGE("UAC2 设备没有 Clock Source，无法设置采样率");
            return false;
        }
        // SET_CUR 到 Clock Source 的 SAM_FREQ_CONTROL，数据是 4 字节小端
        uint8_t data[4] = {
                (uint8_t)(rate & 0xFF),
                (uint8_t)((rate >> 8) & 0xFF),
                (uint8_t)((rate >> 16) & 0xFF),
                (uint8_t)((rate >> 24) & 0xFF),
        };
        // wIndex 顺序要和探测结果一致（有些设备把 Entity ID 放高字节）
        const uint16_t eid  = (uint16_t)(info.clockSourceId & 0xFF);
        const uint16_t acif = (uint16_t)(info.acInterface & 0xFF);
        const uint16_t wIdx = (info.clockIndexMode == 1)
                              ? (uint16_t)((eid << 8) | acif)
                              : (uint16_t)((acif << 8) | eid);
        int r = libusb_control_transfer(
                h,
                0x21,   // host-to-device | class | interface
                UAC2_CS_CUR,
                (uint16_t)(UAC2_SAM_FREQ_CONTROL << 8),
                wIdx,
                data, sizeof(data), 2000);
        if (r < 0) {
            LOGE("UAC2 SET_CUR 采样率 %u 失败: %s", rate, libusb_strerror((libusb_error)r));
            return false;
        }
        LOGI("UAC2 采样率已设为 %u Hz (clock %d)", rate, info.clockSourceId);
        return true;
    }

    // UAC1：SET_CUR 到 isochronous endpoint 的 SAMPLING_FREQ_CONTROL，3 字节小端
    uint8_t data[3] = {
            (uint8_t)(rate & 0xFF),
            (uint8_t)((rate >> 8) & 0xFF),
            (uint8_t)((rate >> 16) & 0xFF),
    };
    int r = libusb_control_transfer(
            h,
            0x22,   // host-to-device | class | endpoint
            UAC2_CS_CUR,   // SET_CUR = 0x01，与 UAC2 相同
            (uint16_t)(UAC1_SAMPLING_FREQ_CONTROL << 8),
            (uint16_t)((alt.dataEpAddr & 0xFF) | ((alt.interfaceNumber & 0xFF) << 8)),
            data, sizeof(data), 2000);
    if (r < 0) {
        LOGE("UAC1 SET_CUR 采样率 %u 失败: %s", rate, libusb_strerror((libusb_error)r));
        return false;
    }
    LOGI("UAC1 采样率已设为 %u Hz (ep 0x%02X)", rate, alt.dataEpAddr);
    return true;
}

bool uac_set_alt(libusb_device_handle *h, int interfaceNumber, int altSetting) {
    int r = libusb_set_interface_alt_setting(h, interfaceNumber, altSetting);
    if (r != 0) {
        LOGE("set_interface_alt_setting(if=%d, alt=%d) 失败: %s",
             interfaceNumber, altSetting, libusb_strerror((libusb_error)r));
        return false;
    }
    LOGI("已切到 if=%d alt=%d", interfaceNumber, altSetting);
    return true;
}

// ---------------------------------------------------------------------------
// UacStream
// ---------------------------------------------------------------------------

UacStream::~UacStream() { stop(); }

bool UacStream::start(libusb_context *ctx, libusb_device_handle *handle, const StreamAlt &alt,
                      uint32_t sampleRate, int subslotBytes) {
    if (running_.load()) {
        LOGW("流已在运行，先 stop");
        stop();
    }
    if (alt.dataEpAddr < 0 || alt.dataEpMaxPacket <= 0) {
        LOGE("alt setting 无效（没有数据端点）");
        return false;
    }

    handle_     = handle;
    ctx_        = ctx;
    alt_        = alt;
    rate_       = sampleRate;
    subslot_    = subslotBytes;
    frameBytes_ = alt.fmt.channels * subslotBytes;
    if (frameBytes_ <= 0) frameBytes_ = 6;

    // 关键：包间隔由总线速度 + bInterval 决定，不能一律当成 1ms
    const int speed = libusb_get_device_speed(libusb_get_device(handle));
    intervalUs_ = uac_interval_us(speed, alt.dataEpInterval);
    // 每个 URB 装约 4ms 音频；4 个 URB 合计约 16ms 缓冲
    packetsPerUrb_ = 4000 / intervalUs_;
    if (packetsPerUrb_ < 2)  packetsPerUrb_ = 2;
    if (packetsPerUrb_ > 64) packetsPerUrb_ = 64;

    fracAcc_ = 0.0;
    underruns_ = 0;
    bytesSent_ = 0;
    samplesSent_ = 0;
    feedbackRaw_ = 0;
    feedbackRate_ = 0.0;
    feedbackCount_ = 0;
    effectiveRate_ = (double)sampleRate;
    running_ = true;
    inFlight_ = 0;

    // 每个 URB 的 buffer 按「包数 × 最大包长」分配
    const int bufLen = packetsPerUrb_ * alt.dataEpMaxPacket;

    for (int i = 0; i < kNumUrbs; i++) {
        buffer_[i].assign(bufLen, 0);
        xfer_[i] = libusb_alloc_transfer(packetsPerUrb_);
        if (!xfer_[i]) {
            LOGE("alloc_transfer 失败，降级到更少 URB");
            // 已分配的交给 stop 清理
            running_ = false;
            stop();
            return false;
        }
        libusb_fill_iso_transfer(xfer_[i], handle_, (unsigned char)alt.dataEpAddr,
                                 buffer_[i].data(), bufLen, packetsPerUrb_,
                                 &UacStream::onTransfer, this, 1000);
        libusb_set_iso_packet_lengths(xfer_[i], alt.dataEpMaxPacket);
    }

    if (source_) source_->onFormat(rate_, alt.fmt.channels, subslot_);

    // 先把所有 URB 填满再一起提交，避免开头欠载
    for (int i = 0; i < kNumUrbs; i++) {
        fillTransfer(xfer_[i]);
        int r = libusb_submit_transfer(xfer_[i]);
        if (r != 0) {
            LOGE("首个 URB 提交失败: %s", libusb_strerror((libusb_error)r));
            running_ = false;
            stop();
            return false;
        }
        inFlight_++;
    }

    eventThread_ = std::thread(&UacStream::eventLoop, this);

    // 启动反馈端点监听（async 设备靠它告诉主机该发多少）
    startFeedback();

    LOGI("iso 流已启动: %u Hz / %d bit / %d ch, ep=0x%02X, 每包 %dus, "
         "%d URB x %d 包 (%.1fms/URB), 每包约 %.3f 采样",
         rate_, subslot_ * 8, alt.fmt.channels, alt.dataEpAddr, intervalUs_,
         kNumUrbs, packetsPerUrb_, packetsPerUrb_ * intervalUs_ / 1000.0,
         (double)rate_ * intervalUs_ / 1000000.0);
    return true;
}

void UacStream::fillTransfer(libusb_transfer *t) {
    int off = 0;
    uint64_t frames = 0;

    // 闭环速率：若收到合法 feedback 值则按平滑后的设备实际晶振频率发包，消除长播漂移
    double targetRate = (double)rate_;
    if (feedbackControlEnabled_.load(std::memory_order_relaxed) &&
        feedbackCount_.load(std::memory_order_relaxed) > 0) {
        double er = effectiveRate_.load(std::memory_order_relaxed);
        if (er > 0.0) targetRate = er;
    }

    for (int i = 0; i < t->num_iso_packets; i++) {
        // 每个包要发 targetRate * intervalUs / 1e6 个采样。
        // 44.1k 在 125µs 下是 5.5125 个，累加器负责长期平均不漂。
        fracAcc_ += targetRate * intervalUs_ / 1000000.0;
        int ns = (int)fracAcc_;
        fracAcc_ -= ns;

        int len = ns * frameBytes_;
        // 上限要用设备上报的 maxPacket，不能读 iso_packet_desc[i].length——
        // 那个字段下面马上就要被改成实际包长，拿它做上限会自我污染。
        const int maxLen = alt_.dataEpMaxPacket;
        if (len > maxLen) {
            LOGW("包长 %d 超过 maxPacket %d，已截断", len, maxLen);
            len = maxLen;
        }
        t->iso_packet_desc[i].length = len;
        t->iso_packet_desc[i].actual_length = 0;

        size_t got = 0;
        if (source_ && len > 0) {
            uint8_t *dst = t->buffer + off;
            got = source_->read(dst, (size_t)len);
            if (got < (size_t)len) {
                std::memset(dst + got, 0, (size_t)len - got);   // 静音补齐
            }
        } else if (len > 0) {
            std::memset(t->buffer + off, 0, (size_t)len);
        }
        off += len;

        // 按实际发出的帧数累加，不要用 rate/1000 的整数除法（44.1k 会丢掉 0.1）
        if (frameBytes_ > 0 && len > 0) frames += (uint64_t)(len / frameBytes_);
    }

    samplesSent_ += frames;
    bytesSent_ += off;
    // libusb 约定：iso transfer 的 length 应等于所有包长之和
    t->length = off;
}

void UacStream::handleComplete(libusb_transfer *t) {
    if (t->status == LIBUSB_TRANSFER_CANCELLED || t->status == LIBUSB_TRANSFER_NO_DEVICE) {
        inFlight_--;
        if (t->status == LIBUSB_TRANSFER_NO_DEVICE) {
            LOGE("设备已断开");
            running_ = false;
        }
        return;
    }

    // 注意：OUT 方向的 iso 包，内核不保证回填 actual_length，
    // 所以不能按 actual_length < length 判断欠载，只能看 URB 状态。
    if (t->status != LIBUSB_TRANSFER_COMPLETED) {
        underruns_++;
        LOGW("iso 传输状态异常: %d", t->status);
    }

    if (!running_.load()) {
        inFlight_--;
        return;
    }

    fillTransfer(t);
    int r = libusb_submit_transfer(t);
    if (r != 0) {
        LOGE("重新提交失败: %s", libusb_strerror((libusb_error)r));
        inFlight_--;
        running_ = false;
        return;
    }
    // inFlight_ 保持不变的（取消->重新提交，仍算在飞）
}

void LIBUSB_CALL UacStream::onTransfer(libusb_transfer *t) {
    static_cast<UacStream *>(t->user_data)->handleComplete(t);
}

void UacStream::eventLoop() {
    while (running_.load() || inFlight_.load() > 0) {
        struct timeval tv;
        tv.tv_sec  = 0;
        tv.tv_usec = 100000;   // 100ms 超时，保证能周期性检查退出条件
        int r = libusb_handle_events_timeout_completed(ctx_, &tv, nullptr);
        if (r < 0 && r != LIBUSB_ERROR_INTERRUPTED) {
            LOGE("handle_events 出错: %s", libusb_strerror((libusb_error)r));
            break;
        }
    }
}

void UacStream::stop() {
    if (!handle_) return;

    bool wasRunning = running_.exchange(false);

    stopFeedback();

    // 取消所有在飞的 URB
    for (int i = 0; i < kNumUrbs; i++) {
        if (xfer_[i] && inFlight_.load() > 0) {
            libusb_cancel_transfer(xfer_[i]);
        }
    }

    if (eventThread_.joinable()) {
        eventThread_.join();
    }

    // 兜底：万一回调一直不来，最多等 1 秒
    for (int spin = 0; spin < 20 && inFlight_.load() > 0; spin++) {
        struct timeval tv; tv.tv_sec = 0; tv.tv_usec = 50000;
        libusb_handle_events_timeout_completed(ctx_, &tv, nullptr);
    }

    for (int i = 0; i < kNumUrbs; i++) {
        if (xfer_[i]) {
            libusb_free_transfer(xfer_[i]);
            xfer_[i] = nullptr;
        }
    }
    inFlight_ = 0;

    // 关键：流结束后切回 alt 0（零带宽），否则某些 DAC 会保持占用、下次打不开
    if (alt_.interfaceNumber >= 0 && wasRunning) {
        libusb_set_interface_alt_setting(handle_, alt_.interfaceNumber, 0);
    }

    LOGI("流已停止，欠载计数=%llu", (unsigned long long)underruns_.load());
    handle_ = nullptr;
}

// ---------------------------------------------------------------------------
// 反馈端点
//
// UAC2 async 设备有自己的时钟，靠 feedback 端点告诉主机「每个 microframe
// 我实际需要多少个采样」。值是无符号 16.16 定点：
//   44.1kHz → 44100/8000 = 5.5125 → 0x00058333
//   48kHz   → 48000/8000 = 6.0     → 0x00060000
//
// 主机如果不看这个值、只管按目标采样率硬发，两者一旦有偏差，
// 设备的 FIFO 就会周期性溢出/抽空，听感是持续的爆音或刺耳噪声。
// ---------------------------------------------------------------------------
bool UacStream::startFeedback() {
    if (alt_.feedbackEpAddr < 0) {
        LOGI("设备没有 feedback 端点，按固定速率发送");
        return false;
    }

    fbXfer_ = libusb_alloc_transfer(1);
    if (!fbXfer_) {
        LOGE("feedback transfer 分配失败");
        return false;
    }

    libusb_fill_iso_transfer(fbXfer_, handle_, (unsigned char)alt_.feedbackEpAddr,
                             fbBuf_, 4, 1, &UacStream::onFeedback, this, 200);
    libusb_set_iso_packet_lengths(fbXfer_, 4);

    int r = libusb_submit_transfer(fbXfer_);
    if (r != 0) {
        LOGE("feedback 提交失败: %s", libusb_strerror((libusb_error)r));
        libusb_free_transfer(fbXfer_);
        fbXfer_ = nullptr;
        return false;
    }
    LOGI("已开始监听 feedback 端点 0x%02X", alt_.feedbackEpAddr);
    return true;
}

void UacStream::stopFeedback() {
    if (!fbXfer_) return;
    libusb_cancel_transfer(fbXfer_);
    // 等回调把 in-flight 清掉（eventLoop 还在跑的话会处理）
    for (int i = 0; i < 10; ++i) {
        struct timeval tv; tv.tv_sec = 0; tv.tv_usec = 20000;
        libusb_handle_events_timeout_completed(ctx_, &tv, nullptr);
    }
    libusb_free_transfer(fbXfer_);
    fbXfer_ = nullptr;
}

void LIBUSB_CALL UacStream::onFeedback(libusb_transfer *t) {
    auto *self = static_cast<UacStream *>(t->user_data);
    if (!self) return;

    if (t->status == LIBUSB_TRANSFER_COMPLETED && t->iso_packet_desc[0].actual_length >= 4) {
        const uint8_t *b = t->buffer;
        uint32_t raw = (uint32_t)b[0] | ((uint32_t)b[1] << 8) |
                       ((uint32_t)b[2] << 16) | ((uint32_t)b[3] << 24);
        // 16.16 定点：整数部分 = 每 microframe 的采样数
        double perFrame = (double)raw / 65536.0;
        double sr = perFrame * (1000000.0 / (double)self->intervalUs_);

        self->feedbackRaw_ = raw;
        self->feedbackRate_ = sr;
        // 合法范围校验（±2%），经一阶指数平滑后驱动 fillTransfer，抗单包抖动
        if (self->rate_ > 0 && sr >= self->rate_ * 0.98 && sr <= self->rate_ * 1.02) {
            double cur = self->effectiveRate_.load(std::memory_order_relaxed);
            if (cur <= 0.0) cur = (double)self->rate_;
            self->effectiveRate_.store(cur * 0.95 + sr * 0.05, std::memory_order_relaxed);
        }
        uint64_t n = self->feedbackCount_ + 1;
        self->feedbackCount_ = n;

        // 每秒打一次，避免刷屏
        if (n % 1000 == 1) {
            LOGI("feedback: raw=0x%08x → %.4f 采样/帧 → 设备实际 %.1f Hz "
                 "(闭环 %.1f Hz / 目标 %u Hz)",
                 raw, perFrame, sr, self->effectiveRate_.load(std::memory_order_relaxed), self->rate_);
        }
    } else if (t->status == LIBUSB_TRANSFER_NO_DEVICE ||
               t->status == LIBUSB_TRANSFER_CANCELLED) {
        return;   // 不再重新提交
    }

    if (self->running_.load()) {
        int r = libusb_submit_transfer(t);
        if (r != 0) {
            LOGW("feedback 重新提交失败: %s", libusb_strerror((libusb_error)r));
        }
    }
}
