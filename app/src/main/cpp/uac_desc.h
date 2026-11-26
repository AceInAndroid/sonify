// UAC (USB Audio Class) 1.0 / 2.0 描述符模型与解析
//
// 设计依据：Linux 内核 sound/usb/card.c + stream.c + clock.c
// HiBy 的驱动也是从同一套代码移植的，所以结构完全对得上。
#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include <libusb.h>
#include <android/log.h>

#define LOG_TAG "HiFiAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---- USB 描述符类型 -------------------------------------------------------
static constexpr uint8_t DESCRIPTOR_CS_INTERFACE = 0x24;
static constexpr uint8_t DESCRIPTOR_CS_ENDPOINT = 0x25;

// ---- Audio Class 子类 -----------------------------------------------------
static constexpr uint8_t SUBCLASS_AUDIOCONTROL = 0x01;
static constexpr uint8_t SUBCLASS_AUDIOSTREAMING = 0x02;

// ---- AC 接口描述符子类型 --------------------------------------------------
static constexpr uint8_t AC_HEADER          = 0x01;
static constexpr uint8_t AC_INPUT_TERMINAL  = 0x02;
static constexpr uint8_t AC_OUTPUT_TERMINAL = 0x03;
static constexpr uint8_t AC_FEATURE_UNIT    = 0x06;   // UAC1 & UAC2 音量/静音单元
static constexpr uint8_t AC_CLOCK_SOURCE    = 0x0A;   // UAC2 only
static constexpr uint8_t AC_CLOCK_SELECTOR  = 0x0B;   // UAC2 only

// ---- Feature Unit 控制选择器 ----------------------------------------------
static constexpr uint8_t UAC_FU_MUTE_CONTROL   = 0x01;
static constexpr uint8_t UAC_FU_VOLUME_CONTROL = 0x02;

// ---- UAC1 音频控制请求码 --------------------------------------------------
static constexpr uint8_t UAC1_SET_CUR = 0x01;
static constexpr uint8_t UAC1_GET_CUR = 0x81;
static constexpr uint8_t UAC1_GET_MIN = 0x82;
static constexpr uint8_t UAC1_GET_MAX = 0x83;
static constexpr uint8_t UAC1_GET_RES = 0x84;

// ---- AS 接口描述符子类型 --------------------------------------------------
static constexpr uint8_t AS_GENERAL    = 0x01;
static constexpr uint8_t AS_FORMAT_TYPE = 0x02;

// ---- Format Type ----------------------------------------------------------
static constexpr uint8_t FORMAT_TYPE_I = 0x01;

// ---- UAC2 控制选择器 ------------------------------------------------------
static constexpr uint8_t UAC2_CS_CUR   = 0x01;
static constexpr uint8_t UAC2_CS_RANGE = 0x02;
static constexpr uint8_t UAC2_SAM_FREQ_CONTROL = 0x01;

// ---- UAC1 端点控制选择器 --------------------------------------------------
static constexpr uint8_t UAC1_SAMPLING_FREQ_CONTROL = 0x01;

// ---- 端点 bmAttributes 位域 ----------------------------------------------
static constexpr uint8_t EP_XFER_MASK   = 0x03;
static constexpr uint8_t EP_XFER_ISO    = 0x01;
static constexpr uint8_t EP_SYNC_MASK   = 0x0C;
static constexpr uint8_t EP_SYNC_ADAPTIVE = 0x08;
static constexpr uint8_t EP_SYNC_SYNC     = 0x0C;
// 0x04 = asynchronous
static constexpr uint8_t EP_USAGE_MASK  = 0x30;
static constexpr uint8_t EP_USAGE_FEEDBACK = 0x10;

struct SampleFormat {
    uint8_t  bitResolution = 0;      // 有效位深，如 24
    uint8_t  subslotSize   = 0;      // 容器字节数，通常 3（24bit）或 4
    uint16_t channels      = 0;
    bool     continuous    = false;  // tSamFreq 是区间还是离散列表
    std::vector<uint32_t> sampleRates;   // 离散列表
    uint32_t minRate = 0, maxRate = 0;   // 连续区间
    bool     rateKnown = false;      // UAC2 若 GET_RANGE 失败则为 false
};

// 一个 alt setting 就是一条可用的流配置
struct StreamAlt {
    int         interfaceNumber = -1;
    int         altSetting      = 0;
    int         dataEpAddr      = -1;
    int         dataEpMaxPacket = 0;
    int         dataEpInterval  = 1;      // bInterval，含意随总线速度变化
    int         feedbackEpAddr  = -1;
    int         feedbackMaxPacket = 0;
    int         feedbackInterval = 0;
    uint8_t     syncType        = 0;   // 1=async 2=adaptive 3=sync
    SampleFormat fmt;
};

// UAC Feature Unit 硬件音量能力（8.8 有符号定点，单位 1/256 dB）
struct HardwareVolumeInfo {
    bool     supported   = false;
    int      unitId      = -1;
    int      sourceId    = -1;
    bool     hasMaster   = false;
    bool     hasMute     = false;
    std::vector<int> channels;    // 若 !hasMaster，记录可写声道（如 MOONRIVER 3 为 {1, 2}）
    // wIndex 编码顺序：-1 未探测 / 0 标准 (acIf<<8)|unitId / 1 反序 (unitId<<8)|acIf
    int      wIndexMode  = -1;
    int16_t  minRaw      = -15360;   // 默认 -60.0 dB
    int16_t  maxRaw      = 0;        // 默认 0.0 dB
    uint16_t resRaw      = 256;      // 默认 1.0 dB
    int16_t  curRaw      = 0;

    double minDb() const { return (double)minRaw / 256.0; }
    double maxDb() const { return (double)maxRaw / 256.0; }
    double curDb() const { return (double)curRaw / 256.0; }
};

struct UacDeviceInfo {
    int      uacVersion = 0;         // 1 或 2
    int      acInterface = -1;
    int      clockSourceId = -1;     // UAC2
    int      clockSelectorId = -1;   // UAC2（可选）
    bool     hasClockSource = false;
    int      clockSourceControls = -1;  // UAC2 bmControls，判断能否读采样率
    // 时钟控制请求的 wIndex 编码顺序：
    //   -1 未探测 / 0 = 标准（高字节 interface、低字节 Entity ID）
    //    1 = 反序（高字节 Entity ID、低字节 interface）——MOONRIVER 3 就是这种
    int      clockIndexMode = -1;
    int      speed = 0;              // libusb_speed
    uint16_t vid = 0, pid = 0;
    std::string productName;
    std::string manufacturerName;
    std::string serialNumber;

    HardwareVolumeInfo hwVol;
    std::vector<StreamAlt> alts;

    // 便于 UI 展示：把所有 alt 支持的采样率去重合并
    std::vector<uint32_t> allSampleRates() const;
};

// bInterval + 总线速度 → 每包间隔微秒数
// high-speed 下 bInterval=1 是 125µs（一个 microframe），full-speed 下是 1ms。
// 这个值搞错会导致发出的数据量差 8 倍。
int uac_interval_us(int speed, int bInterval);

// 解析设备当前的 active config，填充 info。
// 返回 false 时 err 里是原因。
bool uac_parse_device(libusb_device_handle *handle, UacDeviceInfo *info, std::string *err);

// ★ 必须调用，否则控制传输和 iso 全都会被拒。
// Android 上音频接口默认由内核 snd-usb-audio 驱动占用，
// 不 claim（内部会 detach kernel driver）的话：
//   - class-specific control transfer 返回 IO error
//   - set_interface_alt_setting 失败
//   - isochronous 端点无法提交
bool uac_claim_interfaces(libusb_device_handle *handle, const UacDeviceInfo &info,
                          std::string *err);
void uac_release_interfaces(libusb_device_handle *handle, const UacDeviceInfo &info);

// 必须在 uac_claim_interfaces() 之后调用：UAC2 的采样率靠 GET_RANGE 问时钟，
// claim 之前发控制请求会被内核驱动挡掉。
void uac_refresh_rates(libusb_device_handle *handle, UacDeviceInfo *info);

// 必须在 uac_claim_interfaces() 之后调用：探测 Feature Unit 硬件音量范围与 wIndex 顺序
bool uac_probe_hardware_volume(libusb_device_handle *handle, UacDeviceInfo *info);

// 设置 DAC 硬件音量（单位 dB，8.8 定点下发到 Master 或全声道）
bool uac_set_hardware_volume_db(libusb_device_handle *handle, UacDeviceInfo *info,
                                double db, bool mute);

// 试探各种 wIndex 编码，定位采样率控制请求为什么被 STALL
void uac_diagnose_clock(libusb_device_handle *handle, const UacDeviceInfo &info);

// 枚举总线上所有 UAC 设备（用于 UI 列表），不含打开操作。
struct UacDeviceEntry {
    uint16_t vid = 0, pid = 0;
    int      busNumber = 0, deviceAddress = 0;
    std::string productName;
    std::string manufacturerName;
};
std::vector<UacDeviceEntry> uac_enumerate(libusb_context *ctx);

// 把 info 序列化成 JSON 字符串（给 Kotlin 侧诊断页用）
std::string uac_info_to_json(const UacDeviceInfo &info);
