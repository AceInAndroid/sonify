#include "uac_desc.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

// ---------------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------------

static uint16_t rd16(const uint8_t *p) { return (uint16_t)(p[0] | (p[1] << 8)); }
static uint32_t rd32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static std::string json_escape(const std::string &s) {
    std::string o;
    o.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"':  o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n";  break;
            case '\r': o += "\\r";  break;
            case '\t': o += "\\t";  break;
            default:
                if ((unsigned char)c < 0x20) {
                    char b[8];
                    snprintf(b, sizeof(b), "\\u%04x", (unsigned char)c);
                    o += b;
                } else {
                    o += c;
                }
        }
    }
    return o;
}

// 遍历一段 extra 里的所有 class-specific 描述符
// 每个 class-specific 描述符都以 (bLength, bDescriptorType=0x24/0x25, bDescriptorSubtype) 开头
struct CsDesc {
    uint8_t        type;      // 0x24 / 0x25
    uint8_t        subtype;
    const uint8_t *body;      // 指向 bDescriptorSubtype 之后第一个字节
    int            bodyLen;
};

template <typename F>
static void for_each_cs(const uint8_t *extra, int extraLen, F fn) {
    int off = 0;
    while (extra && off + 3 <= extraLen) {
        uint8_t len = extra[off];
        if (len < 3 || off + len > extraLen) break;   // 描述符损坏，停止
        uint8_t type = extra[off + 1];
        if (type == DESCRIPTOR_CS_INTERFACE || type == DESCRIPTOR_CS_ENDPOINT) {
            fn(CsDesc{type, extra[off + 2], extra + off + 3, (int)len - 3});
        }
        off += len;
    }
}

static std::string read_string(libusb_device_handle *h, uint8_t idx) {
    if (idx == 0) return {};
    unsigned char buf[256] = {0};
    int n = libusb_get_string_descriptor_ascii(h, idx, buf, sizeof(buf) - 1);
    if (n <= 0) return {};
    return std::string((char *)buf, (size_t)n);
}

// 按探测到的顺序拼 wIndex
static uint16_t clock_windex(const UacDeviceInfo &info) {
    const uint16_t eid  = (uint16_t)(info.clockSourceId & 0xFF);
    const uint16_t acif = (uint16_t)(info.acInterface & 0xFF);
    if (info.clockIndexMode == 1) return (uint16_t)((eid << 8) | acif);
    return (uint16_t)((acif << 8) | eid);
}

// 两种顺序都试一遍，确定设备用哪种。返回 0=标准 1=反序 -1=都不通
static int detect_clock_index_mode(libusb_device_handle *h, UacDeviceInfo *info) {
    if (info->clockSourceId < 0) return -1;
    const uint16_t eid  = (uint16_t)(info->clockSourceId & 0xFF);
    const uint16_t acif = (uint16_t)(info->acInterface & 0xFF);
    uint8_t buf[8];

    int r = libusb_control_transfer(h, 0xA1, UAC2_CS_CUR, 0x0100,
                                    (uint16_t)((acif << 8) | eid), buf, 4, 800);
    if (r >= 4) {
        info->clockIndexMode = 0;
        LOGI("时钟 wIndex 用标准顺序 0x%04x，当前 %u Hz",
             (unsigned)((acif << 8) | eid), rd32(buf));
        return 0;
    }

    r = libusb_control_transfer(h, 0xA1, UAC2_CS_CUR, 0x0100,
                                (uint16_t)((eid << 8) | acif), buf, 4, 800);
    if (r >= 4) {
        info->clockIndexMode = 1;
        LOGI("时钟 wIndex 用反序 0x%04x（非标准），当前 %u Hz",
             (unsigned)((eid << 8) | acif), rd32(buf));
        return 1;
    }

    info->clockIndexMode = -1;
    LOGW("两种 wIndex 顺序都读不到采样率");
    return -1;
}

static std::string hexdump(const uint8_t *p, int n, int maxBytes = 320) {
    std::string s;
    char b[4];
    int lim = n < maxBytes ? n : maxBytes;
    for (int i = 0; i < lim; i++) {
        snprintf(b, sizeof(b), "%02x", p[i]);
        s += b;
        if (i + 1 < lim) s += ' ';
    }
    if (n > lim) s += " ...";
    return s;
}

// high-speed 下 iso 的 bInterval 是 2^(n-1) 个 microframe（125µs 一个）
// full-speed 下是 n 个 1ms 帧
int uac_interval_us(int speed, int bInterval) {
    if (bInterval < 1) bInterval = 1;
    if (speed == LIBUSB_SPEED_HIGH || speed == LIBUSB_SPEED_SUPER) {
        int shift = bInterval - 1;
        if (shift > 4) shift = 4;      // 上限 16 microframes = 2ms
        return 125 * (1 << shift);
    }
    if (speed == LIBUSB_SPEED_FULL || speed == LIBUSB_SPEED_LOW) {
        return bInterval * 1000;
    }
    // 速度未知：现代 DAC 绝大多数跑 high-speed，按 125µs 猜。
    // 猜错的表现是播放速度差 8 倍，可以在设置里手动覆盖。
    return 125;
}

// ---------------------------------------------------------------------------
// UAC2：向 Clock Source 发 GET_RANGE 查支持的采样率
// ---------------------------------------------------------------------------
static bool uac2_query_clock_range(libusb_device_handle *h, const UacDeviceInfo &info,
                                   SampleFormat *fmt) {
    const int clockId     = info.clockSourceId;
    const uint16_t wValue = (uint16_t)(UAC2_SAM_FREQ_CONTROL << 8);
    const uint16_t wIndex = clock_windex(info);

    // 先试 GET_CUR 读当前值，这既能确认控制通道通不通，
    // 也能在 GET_RANGE 不被支持时至少知道一个可用采样率。
    {
        uint8_t cur[4] = {0};
        int rc = libusb_control_transfer(h, 0xA1, UAC2_CS_CUR, wValue, wIndex,
                                         cur, sizeof(cur), 1000);
        if (rc >= 4) {
            uint32_t v = rd32(cur);
            LOGI("Clock[%d] GET_CUR 采样率 = %u Hz", clockId, v);
        } else {
            LOGW("Clock[%d] GET_CUR 失败 r=%d (%s), wValue=0x%04x wIndex=0x%04x",
                 clockId, rc, libusb_strerror((libusb_error)rc), wValue, wIndex);
        }
    }

    // 每个子区间 12 字节（dMIN/dMAX/dRES 各 4 字节），前面的 wNumSubRanges 占 2 字节。
    // MOONRIVER 3 报了 10 个区间，缓冲区小了会被截断。
    uint8_t buf[2 + 12 * 32];
    int r = libusb_control_transfer(h, 0xA1, UAC2_CS_RANGE, wValue, wIndex,
                                    buf, sizeof(buf), 1000);

    if (r < 2) {
        LOGW("Clock[%d] GET_RANGE 失败 r=%d (%s), wValue=0x%04x wIndex=0x%04x",
             clockId, r, libusb_strerror((libusb_error)r), wValue, wIndex);
        return false;
    }

    LOGI("Clock[%d] GET_RANGE r=%d raw=%s", clockId, r, hexdump(buf, r, 60).c_str());

    LOGI("Clock[%d] GET_RANGE r=%d raw=%s", clockId, r, hexdump(buf, r, 60).c_str());

    uint16_t n = rd16(buf);
    if (n == 0) {
        LOGW("Clock[%d] GET_RANGE 返回 0 个子区间（采样率固定或不可读）", clockId);
        return false;
    }
    const int maxFit = (r - 2) / 12;
    if ((int)n > maxFit) {
        LOGW("Clock[%d] GET_RANGE 声明 %u 个区间但只回来 %d 字节（最多 %d 个），按 %d 处理",
             clockId, n, r, maxFit, maxFit);
        n = (uint16_t)maxFit;
        if (n == 0) return false;
    }

    fmt->sampleRates.clear();
    for (int i = 0; i < n; i++) {
        const uint8_t *p = buf + 2 + i * 12;
        uint32_t dmin = rd32(p);
        uint32_t dmax = rd32(p + 4);
        uint32_t dres = rd32(p + 8);
        if (dmin == dmax) {
            // 单点区间：设备用「dMIN == dMAX、dRES = 0」来表示一个离散采样率。
            // 这是很常见的写法，不能当成连续区间吞掉。
            fmt->sampleRates.push_back(dmin);
        } else if (dres == 0) {
            // 真正的连续区间
            fmt->continuous = true;
            fmt->minRate = dmin;
            fmt->maxRate = dmax;
        } else {
            for (uint32_t f = dmin; f <= dmax && fmt->sampleRates.size() < 64; f += dres) {
                fmt->sampleRates.push_back(f);
            }
        }
    }
    std::sort(fmt->sampleRates.begin(), fmt->sampleRates.end());
    fmt->sampleRates.erase(std::unique(fmt->sampleRates.begin(), fmt->sampleRates.end()),
                           fmt->sampleRates.end());
    fmt->rateKnown = !fmt->sampleRates.empty() || fmt->continuous;

    if (fmt->rateKnown) {
        LOGI("Clock[%d] 支持 %zu 个采样率，范围 %u..%u",
             clockId, fmt->sampleRates.size(),
             fmt->sampleRates.empty() ? 0 : fmt->sampleRates.front(),
             fmt->sampleRates.empty() ? 0 : fmt->sampleRates.back());
    }
    return fmt->rateKnown;
}

// ---------------------------------------------------------------------------
// 主解析
// ---------------------------------------------------------------------------
bool uac_parse_device(libusb_device_handle *handle, UacDeviceInfo *info, std::string *err) {
    libusb_device *dev = libusb_get_device(handle);
    if (!dev) { if (err) *err = "libusb_get_device 返回空"; return false; }

    info->speed = libusb_get_device_speed(dev);
    {
        const char *sp = "unknown";
        switch (info->speed) {
            case LIBUSB_SPEED_LOW:   sp = "low";   break;
            case LIBUSB_SPEED_FULL:  sp = "full";  break;
            case LIBUSB_SPEED_HIGH:  sp = "high";  break;
            case LIBUSB_SPEED_SUPER: sp = "super"; break;
            default: break;
        }
        LOGI("总线速度: %s (%d)", sp, info->speed);
    }

    libusb_device_descriptor dd{};
    if (libusb_get_device_descriptor(dev, &dd) != 0) {
        if (err) *err = "读取 device descriptor 失败";
        return false;
    }
    info->vid = dd.idVendor;
    info->pid = dd.idProduct;
    info->productName      = read_string(handle, dd.iProduct);
    info->manufacturerName = read_string(handle, dd.iManufacturer);
    info->serialNumber     = read_string(handle, dd.iSerialNumber);

    libusb_config_descriptor *cfg = nullptr;
    int r = libusb_get_active_config_descriptor(dev, &cfg);
    if (r != 0 || !cfg) {
        if (err) *err = std::string("读取 config descriptor 失败: ") + libusb_strerror((libusb_error)r);
        return false;
    }

    bool foundAc = false;
    int playbackOutSourceId = -1;
    std::vector<HardwareVolumeInfo> candidateFus;

    // ---------- 第一遍：音频控制接口 ----------
    for (int i = 0; i < cfg->bNumInterfaces; i++) {
        const libusb_interface &itf = cfg->interface[i];
        for (int j = 0; j < itf.num_altsetting; j++) {
            const libusb_interface_descriptor &d = itf.altsetting[j];
            if (d.bInterfaceClass != LIBUSB_CLASS_AUDIO) continue;
            if (d.bInterfaceSubClass != SUBCLASS_AUDIOCONTROL) continue;
            if (foundAc) continue;

            info->acInterface = d.bInterfaceNumber;
            foundAc = true;

            LOGI("AC 接口 %d 的 class-specific 描述符（%d 字节）: %s",
                 d.bInterfaceNumber, d.extra_length,
                 hexdump(d.extra, d.extra_length).c_str());

            for_each_cs(d.extra, d.extra_length, [&](const CsDesc &cs) {
                if (cs.type != DESCRIPTOR_CS_INTERFACE) return;

                if (cs.subtype == AC_HEADER && cs.bodyLen >= 2) {
                    // bcdADC 紧跟在 subtype 之后
                    uint16_t bcd = rd16(cs.body);
                    info->uacVersion = (bcd >= 0x0200) ? 2 : 1;
                    LOGI("AC Header: bcdADC=0x%04x → UAC%d", bcd, info->uacVersion);
                } else if (cs.subtype == AC_INPUT_TERMINAL && cs.bodyLen >= 3) {
                    uint16_t ttype = rd16(cs.body + 1);
                    LOGI("Input Terminal: id=%d type=0x%04x", cs.body[0], ttype);
                } else if (cs.subtype == AC_OUTPUT_TERMINAL && cs.bodyLen >= 5) {
                    uint16_t ttype = rd16(cs.body + 1);
                    // UAC1: [0]bTerminalID [1..2]wTerminalType [3]bAssocTerminal [4]bSourceID
                    // UAC2: [0]bTerminalID [1..2]wTerminalType [3]bAssocTerminal [4]bSourceID [5]bCSourceID
                    int srcId = cs.body[4];
                    // 0x0300..0x03FF 为输出终端（扬声器/耳机等），用于精准匹配播放通路 Feature Unit
                    if ((ttype & 0xFF00) == 0x0300 && playbackOutSourceId < 0) {
                        playbackOutSourceId = srcId;
                    }
                    LOGI("Output Terminal: id=%d type=0x%04x src=%d", cs.body[0], ttype, srcId);
                } else if (cs.subtype == AC_FEATURE_UNIT && cs.bodyLen >= 4) {
                    HardwareVolumeInfo fu;
                    fu.unitId   = cs.body[0];
                    fu.sourceId = cs.body[1];
                    if (info->uacVersion >= 2) {
                        // UAC2 Feature Unit: bUnitID(1) bSourceID(1) bmaControls[0..N](4 each) iFeature(1)
                        int numCh = (cs.bodyLen - 3) / 4;
                        for (int ch = 0; ch < numCh; ch++) {
                            uint32_t bm = rd32(cs.body + 2 + ch * 4);
                            bool muteCap = (bm & 0x03u) != 0;
                            bool volCap  = ((bm >> 2) & 0x03u) != 0;
                            if (muteCap && ch == 0) fu.hasMute = true;
                            if (volCap) {
                                if (ch == 0) fu.hasMaster = true;
                                else fu.channels.push_back(ch);
                            }
                        }
                    } else {
                        // UAC1 Feature Unit: bUnitID(1) bSourceID(1) bControlSize(1) bmaControls[0..N] iFeature(1)
                        int ctrlSize = cs.body[2];
                        if (ctrlSize >= 1 && ctrlSize <= 4 && cs.bodyLen >= 4 + ctrlSize) {
                            int numCh = (cs.bodyLen - 4) / ctrlSize;
                            for (int ch = 0; ch < numCh; ch++) {
                                uint32_t bm = cs.body[3 + ch * ctrlSize];
                                if (ctrlSize >= 2) bm |= ((uint32_t)cs.body[3 + ch * ctrlSize + 1] << 8);
                                if ((bm & 0x01u) && ch == 0) fu.hasMute = true;
                                if (bm & 0x02u) {
                                    if (ch == 0) fu.hasMaster = true;
                                    else fu.channels.push_back(ch);
                                }
                            }
                        }
                    }
                    LOGI("Feature Unit: id=%d src=%d masterVol=%d chVolCount=%zu mute=%d",
                         fu.unitId, fu.sourceId, fu.hasMaster ? 1 : 0, fu.channels.size(),
                         fu.hasMute ? 1 : 0);
                    if (fu.hasMaster || !fu.channels.empty()) {
                        candidateFus.push_back(fu);
                    }
                } else if (cs.subtype == AC_CLOCK_SOURCE && cs.bodyLen >= 1) {
                    info->clockSourceId = cs.body[0];
                    info->hasClockSource = true;
                    // UAC2 Clock Source: bClockID, bmAttributes, bmControls, bAssocTerminal, iClockSource
                    if (cs.bodyLen >= 3) info->clockSourceControls = cs.body[2];
                    LOGI("Clock Source: id=%d bmAttributes=0x%02x bmControls=0x%02x",
                         cs.body[0], cs.bodyLen >= 2 ? cs.body[1] : 0,
                         cs.bodyLen >= 3 ? cs.body[2] : 0);
                } else if (cs.subtype == AC_CLOCK_SELECTOR && cs.bodyLen >= 1) {
                    info->clockSelectorId = cs.body[0];
                    LOGI("Clock Selector: id=%d", cs.body[0]);
                } else {
                    LOGI("AC 其它实体: subtype=0x%02x len=%d body=%s",
                         cs.subtype, cs.bodyLen + 3, hexdump(cs.body, cs.bodyLen, 32).c_str());
                }
            });
        }
    }

    // 优先绑定连向播放 Output Terminal 的 Feature Unit（避免带麦小尾巴误控麦克风）
    if (!candidateFus.empty()) {
        const HardwareVolumeInfo *chosen = &candidateFus.front();
        if (playbackOutSourceId >= 0) {
            for (const auto &fu : candidateFus) {
                if (fu.unitId == playbackOutSourceId) {
                    chosen = &fu;
                    break;
                }
            }
        }
        info->hwVol = *chosen;
    }

    if (!foundAc) {
        libusb_free_config_descriptor(cfg);
        if (err) *err = "不是 UAC 设备（没找到 Audio Control 接口）";
        return false;
    }

    // UAC1 没有 Clock Source，版本号也可能因为 header 缺失而没读到
    if (info->uacVersion == 0) info->uacVersion = info->hasClockSource ? 2 : 1;

    // ---------- 第二遍：音频流接口 ----------
    for (int i = 0; i < cfg->bNumInterfaces; i++) {
        const libusb_interface &itf = cfg->interface[i];
        for (int j = 0; j < itf.num_altsetting; j++) {
            const libusb_interface_descriptor &d = itf.altsetting[j];
            if (d.bInterfaceClass != LIBUSB_CLASS_AUDIO) continue;
            if (d.bInterfaceSubClass != SUBCLASS_AUDIOSTREAMING) continue;
            // alt 0 是零带宽（静音）配置，没有端点，跳过
            if (d.bAlternateSetting == 0 || d.bNumEndpoints == 0) continue;

            StreamAlt alt;
            alt.interfaceNumber = d.bInterfaceNumber;
            alt.altSetting      = d.bAlternateSetting;

            // 先收 AS General 里的通道数，再收 Format Type I 的位深
            bool sawFormat = false;
            for_each_cs(d.extra, d.extra_length, [&](const CsDesc &cs) {
                if (cs.type != DESCRIPTOR_CS_INTERFACE) return;

                if (cs.subtype == AS_GENERAL && cs.bodyLen >= 1) {
                    if (info->uacVersion >= 2) {
                        // UAC2 AS Interface Header（相对 subtype 之后的偏移）:
                        //   [0] bTerminalLink  [1] bmControls  [2] bFormatType
                        //   [3..6] bmFormats(4)  [7] bNrChannels
                        //   [8..11] bmChannelConfig(4)  [12] iChannelNames
                        // 注意 bNrChannels 在 [7]，不是 [6]——[6] 是 bmFormats 的最后一个字节。
                        if (cs.bodyLen >= 8) alt.fmt.channels = cs.body[7];
                    }
                    // UAC1 的通道数在后面的 Format Type I 描述符里，这里不用管
                } else if (cs.subtype == AS_FORMAT_TYPE && cs.bodyLen >= 1) {
                    if (cs.body[0] != FORMAT_TYPE_I) {
                        LOGW("不是 Format Type I（bFormatType=%u），跳过", cs.body[0]);
                        return;
                    }
                    if (info->uacVersion >= 2) {
                        // UAC2 Format Type I: bFormatType(1) bSubslotSize(1) bBitResolution(1)
                        if (cs.bodyLen >= 3) {
                            alt.fmt.subslotSize   = cs.body[1];
                            alt.fmt.bitResolution = cs.body[2];
                            sawFormat = true;
                        }
                    } else {
                        // UAC1 Format Type I:
                        // bFormatType(1) bNrChannels(1) bSubframeSize(1) bBitResolution(1)
                        // bSamFreqType(1) tSamFreq[n][3]
                        if (cs.bodyLen >= 5) {
                            alt.fmt.channels      = cs.body[1];
                            alt.fmt.subslotSize   = cs.body[2];
                            alt.fmt.bitResolution = cs.body[3];
                            uint8_t nFreq         = cs.body[4];
                            if (cs.bodyLen >= 5 + nFreq * 3) {
                                for (int k = 0; k < nFreq; k++) {
                                    const uint8_t *p = cs.body + 5 + k * 3;
                                    uint32_t f = (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16);
                                    if (f) alt.fmt.sampleRates.push_back(f);
                                }
                                std::sort(alt.fmt.sampleRates.begin(), alt.fmt.sampleRates.end());
                                alt.fmt.rateKnown = !alt.fmt.sampleRates.empty();
                            }
                        }
                        sawFormat = true;
                    }
                }
            });

            if (!sawFormat) continue;

            // 端点：数据端点 + feedback 端点
            for (int k = 0; k < d.bNumEndpoints; k++) {
                const libusb_endpoint_descriptor &ep = d.endpoint[k];
                bool isIso = (ep.bmAttributes & EP_XFER_MASK) == EP_XFER_ISO;
                if (!isIso) continue;

                bool isFeedback = (ep.bmAttributes & EP_USAGE_MASK) == EP_USAGE_FEEDBACK;
                int  maxPacket  = ep.wMaxPacketSize & 0x7FF;
                // high-speed 高带宽包：高 2 位是额外事务数
                int extraTrans  = (ep.wMaxPacketSize >> 11) & 0x03;
                if (extraTrans) maxPacket *= (extraTrans + 1);

                if (isFeedback) {
                    // feedback 一定是 IN 方向（bit7=1），UAC2 下是 4 字节 16.16 定点
                    alt.feedbackEpAddr     = ep.bEndpointAddress;
                    alt.feedbackMaxPacket  = maxPacket;
                    alt.feedbackInterval   = ep.bInterval;
                } else if ((ep.bEndpointAddress & 0x80) == 0) {
                    // OUT 方向 = 主机送数据给 DAC
                    alt.dataEpAddr      = ep.bEndpointAddress;
                    alt.dataEpMaxPacket = maxPacket;
                    alt.dataEpInterval  = ep.bInterval;

                    switch (ep.bmAttributes & EP_SYNC_MASK) {
                        case EP_SYNC_ADAPTIVE: alt.syncType = 2; break;
                        case EP_SYNC_SYNC:     alt.syncType = 3; break;
                        default:               alt.syncType = 1; break;  // asynchronous
                    }
                }
            }

            if (alt.dataEpAddr < 0) continue;

            // UAC2 的采样率不在描述符里，要问 Clock Source（GET_RANGE）。
            // 但这必须等到接口被 claim 之后才能做——claim 之前控制传输
            // 会被内核驱动挡住，返回 IO error。所以这里只占位，
            // 真正的查询在 uac_refresh_rates() 里，由调用方在 claim 后触发。

            LOGI("ALT if=%d alt=%d ep=0x%02x pkt=%d bInterval=%d (%dus) sync=%d "
                 "bits=%u/%u ch=%u rates=%zu",
                 alt.interfaceNumber, alt.altSetting, alt.dataEpAddr, alt.dataEpMaxPacket,
                 alt.dataEpInterval, uac_interval_us(info->speed, alt.dataEpInterval),
                 alt.syncType, alt.fmt.bitResolution, alt.fmt.subslotSize, alt.fmt.channels,
                 alt.fmt.sampleRates.size());

            info->alts.push_back(alt);
        }
    }

    libusb_free_config_descriptor(cfg);

    if (info->alts.empty()) {
        if (err) *err = "没找到可用的音频流 alt setting（设备可能只支持 USB Audio 之外的模式）";
        return false;
    }
    return true;
}

std::vector<uint32_t> UacDeviceInfo::allSampleRates() const {
    std::vector<uint32_t> v;
    for (const auto &a : alts) {
        for (uint32_t r : a.fmt.sampleRates) v.push_back(r);
        if (a.fmt.continuous) {
            // 连续区间只放常见几个，UI 上够用
            for (uint32_t r : {44100u, 48000u, 88200u, 96000u, 176400u, 192000u,
                               352800u, 384000u}) {
                if (r >= a.fmt.minRate && r <= a.fmt.maxRate) v.push_back(r);
            }
        }
    }
    std::sort(v.begin(), v.end());
    v.erase(std::unique(v.begin(), v.end()), v.end());

    // 有些设备不支持 GET_RANGE（时钟不可读），这时给一组常见值兜底。
    // 真正能不能用要靠 SET_CUR 的返回值判断——设备会拒绝不支持的值。
    if (v.empty()) {
        v = {44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000};
    }
    return v;
}

std::vector<UacDeviceEntry> uac_enumerate(libusb_context *ctx) {
    std::vector<UacDeviceEntry> out;
    libusb_device **list = nullptr;
    ssize_t n = libusb_get_device_list(ctx, &list);
    if (n < 0) return out;

    for (ssize_t i = 0; i < n; i++) {
        libusb_device *d = list[i];
        libusb_device_descriptor dd{};
        if (libusb_get_device_descriptor(d, &dd) != 0) continue;

        libusb_config_descriptor *cfg = nullptr;
        if (libusb_get_active_config_descriptor(d, &cfg) != 0 || !cfg) continue;

        bool isAudio = false;
        for (int a = 0; a < cfg->bNumInterfaces && !isAudio; a++) {
            const libusb_interface &itf = cfg->interface[a];
            for (int b = 0; b < itf.num_altsetting; b++) {
                const auto &idesc = itf.altsetting[b];
                if (idesc.bInterfaceClass == LIBUSB_CLASS_AUDIO &&
                    idesc.bInterfaceSubClass == SUBCLASS_AUDIOSTREAMING) {
                    isAudio = true;
                    break;
                }
            }
        }
        libusb_free_config_descriptor(cfg);
        if (!isAudio) continue;

        UacDeviceEntry e;
        e.vid = dd.idVendor;
        e.pid = dd.idProduct;
        e.busNumber     = libusb_get_bus_number(d);
        e.deviceAddress = libusb_get_device_address(d);

        // 枚举阶段只能开设备才能读字符串，但没权限会失败，失败就留空
        libusb_device_handle *h = nullptr;
        if (libusb_open(d, &h) == 0 && h) {
            e.productName      = read_string(h, dd.iProduct);
            e.manufacturerName = read_string(h, dd.iManufacturer);
            libusb_close(h);
        }
        out.push_back(e);
    }
    libusb_free_device_list(list, 1);
    return out;
}

std::string uac_info_to_json(const UacDeviceInfo &info) {
    char head[768];
    snprintf(head, sizeof(head),
             "{\"uacVersion\":%d,\"vid\":\"0x%04X\",\"pid\":\"0x%04X\","
             "\"product\":\"%s\",\"manufacturer\":\"%s\",\"serial\":\"%s\","
             "\"acInterface\":%d,\"clockSourceId\":%d,\"hasClockSource\":%s,"
             "\"hasHwVol\":%s,\"hwVolUnitId\":%d,\"hwVolMinDb\":%.2f,\"hwVolMaxDb\":%.2f,"
             "\"speed\":%d,\"alts\":[",
             info.uacVersion, info.vid, info.pid,
             json_escape(info.productName).c_str(),
             json_escape(info.manufacturerName).c_str(),
             json_escape(info.serialNumber).c_str(),
             info.acInterface, info.clockSourceId,
             info.hasClockSource ? "true" : "false",
             info.hwVol.supported ? "true" : "false",
             info.hwVol.unitId,
             info.hwVol.minDb(),
             info.hwVol.maxDb(),
             info.speed);

    std::string s = head;
    for (size_t i = 0; i < info.alts.size(); i++) {
        const auto &a = info.alts[i];
        char b[768];
        snprintf(b, sizeof(b),
                 "%s{\"iface\":%d,\"alt\":%d,\"ep\":\"0x%02X\",\"maxPacket\":%d,"
                 "\"interval\":%d,\"intervalUs\":%d,"
                 "\"feedbackEp\":\"0x%02X\",\"feedbackMaxPacket\":%d,"
                 "\"syncType\":%d,\"bits\":%u,\"subslot\":%u,\"channels\":%u,"
                 "\"continuous\":%s,\"minRate\":%u,\"maxRate\":%u,\"rates\":[",
                 i ? "," : "", a.interfaceNumber, a.altSetting, a.dataEpAddr,
                 a.dataEpMaxPacket, a.dataEpInterval,
                 uac_interval_us(info.speed, a.dataEpInterval),
                 a.feedbackEpAddr, a.feedbackMaxPacket,
                 a.syncType, a.fmt.bitResolution, a.fmt.subslotSize, a.fmt.channels,
                 a.fmt.continuous ? "true" : "false", a.fmt.minRate, a.fmt.maxRate);
        s += b;
        for (size_t k = 0; k < a.fmt.sampleRates.size(); k++) {
            s += (k ? "," : "");
            s += std::to_string(a.fmt.sampleRates[k]);
        }
        s += "]}";
    }
    s += "]}";
    return s;
}

// ---------------------------------------------------------------------------
// 接口 claim
//
// 关键：Android 上 USB 音频设备会被内核 snd-usb-audio 驱动自动绑定。
// 不 detach 并 claim，所有 class-specific 控制请求都会返回 IO error，
// alt setting 也切不动，iso 端点更无法提交。
// libusb_claim_interface 内部会尝试 USBDEVFS_DISCONNECT_CLAIM 来踢掉内核驱动。
// ---------------------------------------------------------------------------
bool uac_claim_interfaces(libusb_device_handle *handle, const UacDeviceInfo &info,
                          std::string *err) {
    if (info.acInterface >= 0) {
        int r = libusb_claim_interface(handle, info.acInterface);
        if (r != 0) {
            std::string msg = std::string("claim AC 接口 ") + std::to_string(info.acInterface) +
                              " 失败: " + libusb_strerror((libusb_error)r);
            LOGW("%s", msg.c_str());
            if (err) *err = msg;
            return false;
        }
        LOGI("已 claim AC 接口 %d", info.acInterface);
    }

    // 把所有 streaming 接口都 claim 掉（同一接口号只 claim 一次）
    std::vector<int> done;
    for (const auto &a : info.alts) {
        if (a.interfaceNumber < 0) continue;
        bool already = false;
        for (int d : done) if (d == a.interfaceNumber) { already = true; break; }
        if (already) continue;
        done.push_back(a.interfaceNumber);

        int r = libusb_claim_interface(handle, a.interfaceNumber);
        if (r != 0) {
            std::string msg = std::string("claim 流接口 ") + std::to_string(a.interfaceNumber) +
                              " 失败: " + libusb_strerror((libusb_error)r);
            LOGW("%s", msg.c_str());
            if (err) *err = msg;
            return false;
        }
        LOGI("已 claim 流接口 %d", a.interfaceNumber);
    }
    return true;
}

void uac_release_interfaces(libusb_device_handle *handle, const UacDeviceInfo &info) {
    for (const auto &a : info.alts) {
        libusb_release_interface(handle, a.interfaceNumber);
    }
    if (info.acInterface >= 0) {
        libusb_release_interface(handle, info.acInterface);
    }
}

// ---------------------------------------------------------------------------
// claim 之后再查询采样率
//
// 描述符里没有 UAC2 的采样率，必须 GET_RANGE 问时钟实体。
// 而控制传输要求接口已经从内核驱动手里抢过来，否则一律 IO error。
// 所以这个函数必须在 uac_claim_interfaces() 之后调用。
// ---------------------------------------------------------------------------
void uac_refresh_rates(libusb_device_handle *handle, UacDeviceInfo *info) {
    if (!info || info->uacVersion < 2 || !info->hasClockSource) return;
    if (info->clockSourceId < 0) return;

    if (info->clockIndexMode < 0) {
        detect_clock_index_mode(handle, info);
    }

    for (auto &a : info->alts) {
        if (a.fmt.rateKnown) continue;
        bool ok = uac2_query_clock_range(handle, *info, &a.fmt);
        if (ok) {
            LOGI("ALT if=%d alt=%d 采样率查询成功: %zu 个",
                 a.interfaceNumber, a.altSetting, a.fmt.sampleRates.size());
        }
    }
}

// ---------------------------------------------------------------------------
// 时钟控制请求诊断
//
// 有些设备的 Clock Source 对标准参数返回 STALL，需要试探哪种 wIndex 编码可用。
// UAC2 规范里 wIndex 是「高字节 interface、低字节 Entity ID」，
// 但实现上常见的变体有：只放 Entity ID、把两者颠倒、或者用 streaming 接口号。
// ---------------------------------------------------------------------------
void uac_diagnose_clock(libusb_device_handle *h, const UacDeviceInfo &info) {
    if (info.uacVersion < 2 || info.clockSourceId < 0) {
        LOGI("诊断跳过：不是 UAC2 或没有 Clock Source");
        return;
    }
    const int eid  = info.clockSourceId;
    const int acif = info.acInterface;

    struct T { uint8_t rt; uint8_t req; uint16_t val; uint16_t idx; const char *name; };
    const T tries[] = {
        {0xA1, 0x01, 0x0100, (uint16_t)((acif << 8) | eid), "GET_CUR  acIf<<8|eid"},
        {0xA1, 0x01, 0x0100, (uint16_t)eid,                 "GET_CUR  eid"},
        {0xA1, 0x01, 0x0100, (uint16_t)((eid << 8) | acif), "GET_CUR  eid<<8|acIf"},
        {0xA1, 0x01, 0x0100, (uint16_t)((3 << 8) | eid),    "GET_CUR  asIf<<8|eid"},
        {0xA1, 0x01, 0x0100, 0x0000,                        "GET_CUR  idx=0"},
        {0xA1, 0x02, 0x0100, (uint16_t)((acif << 8) | eid), "GET_RANGE acIf<<8|eid"},
        {0xA1, 0x02, 0x0100, (uint16_t)eid,                 "GET_RANGE eid"},
    };

    uint8_t buf[64];
    for (const auto &t : tries) {
        memset(buf, 0, sizeof(buf));
        int r = libusb_control_transfer(h, t.rt, t.req, t.val, t.idx, buf, 16, 500);
        if (r >= 0) {
            LOGI("诊断 %-22s → r=%d  data=%02x %02x %02x %02x",
                 t.name, r, buf[0], buf[1], buf[2], buf[3]);
        } else {
            LOGW("诊断 %-22s → r=%d %s", t.name, r, libusb_strerror((libusb_error)r));
        }
    }
}

// ---------------------------------------------------------------------------
// 硬件音量控制（UAC1 / UAC2 Feature Unit）
// ---------------------------------------------------------------------------

static uint16_t fu_windex(const UacDeviceInfo &info, int mode) {
    const uint16_t uid  = (uint16_t)(info.hwVol.unitId & 0xFF);
    const uint16_t acif = (uint16_t)(info.acInterface & 0xFF);
    // mode=1: (unitId<<8)|acIf（UAC2 规范实际上高字节就是 Entity ID，MOONRIVER 3 也是这种）
    // mode=0: (acIf<<8)|unitId
    if (mode == 1) return (uint16_t)((uid << 8) | acif);
    return (uint16_t)((acif << 8) | uid);
}

bool uac_probe_hardware_volume(libusb_device_handle *handle, UacDeviceInfo *info) {
    if (!handle || !info) return false;
    auto &fv = info->hwVol;
    fv.supported = false;
    if (fv.unitId < 0 || (!fv.hasMaster && fv.channels.empty())) {
        LOGI("设备无可用 Feature Unit 硬件音量，将使用软件对数音量");
        return false;
    }

    const int probeCh = fv.hasMaster ? 0 : fv.channels.front();
    const uint16_t wValue = (uint16_t)((UAC_FU_VOLUME_CONTROL << 8) | (probeCh & 0xFF));
    const uint8_t getCurReq = (info->uacVersion >= 2) ? UAC2_CS_CUR : UAC1_GET_CUR;

    // 两种 wIndex 顺序都试一遍，优先试与 clockIndexMode 一致的那种（默认先试 1: uid<<8|acIf）
    int order[2] = {1, 0};
    if (info->clockIndexMode == 0) {
        order[0] = 0; order[1] = 1;
    }

    uint8_t curBuf[2] = {0, 0};
    int matchedMode = -1;
    for (int m : order) {
        uint16_t wIdx = fu_windex(*info, m);
        int r = libusb_control_transfer(handle, 0xA1, getCurReq, wValue, wIdx, curBuf, 2, 800);
        if (r >= 2) {
            matchedMode = m;
            fv.curRaw = (int16_t)rd16(curBuf);
            break;
        }
    }

    if (matchedMode < 0) {
        LOGW("Feature Unit[%d] 两种 wIndex 均无法 GET_CUR，回退软件音量", fv.unitId);
        return false;
    }

    fv.wIndexMode = matchedMode;
    const uint16_t wIdx = fu_windex(*info, matchedMode);

    if (info->uacVersion >= 2) {
        // UAC2 GET_RANGE: wNumSubRanges(2) + N * { wMIN(2), wMAX(2), wRES(2) }
        uint8_t rbuf[2 + 6 * 16] = {0};
        int r = libusb_control_transfer(handle, 0xA1, UAC2_CS_RANGE, wValue, wIdx,
                                        rbuf, sizeof(rbuf), 1000);
        if (r >= 8) {
            uint16_t n = rd16(rbuf);
            int maxFit = (r - 2) / 6;
            if ((int)n > maxFit) n = (uint16_t)maxFit;
            if (n > 0) {
                int16_t minV = (int16_t)rd16(rbuf + 2);
                int16_t maxV = (int16_t)rd16(rbuf + 4);
                uint16_t resV = rd16(rbuf + 6);
                for (int i = 1; i < n; i++) {
                    const uint8_t *p = rbuf + 2 + i * 6;
                    int16_t lo = (int16_t)rd16(p);
                    int16_t hi = (int16_t)rd16(p + 2);
                    if (lo < minV) minV = lo;
                    if (hi > maxV) maxV = hi;
                }
                // 部分 DAC 用 0x8000 (-128dB) 表示静音下限，保留真实范围
                fv.minRaw = minV;
                fv.maxRaw = maxV;
                fv.resRaw = resV > 0 ? resV : 256;
            }
        } else {
            LOGW("FU[%d] UAC2 GET_RANGE 失败 (%s)，使用默认 -60..0 dB",
                 fv.unitId, libusb_strerror((libusb_error)r));
        }
    } else {
        uint8_t b[2];
        if (libusb_control_transfer(handle, 0xA1, UAC1_GET_MIN, wValue, wIdx, b, 2, 800) >= 2) {
            fv.minRaw = (int16_t)rd16(b);
        }
        if (libusb_control_transfer(handle, 0xA1, UAC1_GET_MAX, wValue, wIdx, b, 2, 800) >= 2) {
            fv.maxRaw = (int16_t)rd16(b);
        }
        if (libusb_control_transfer(handle, 0xA1, UAC1_GET_RES, wValue, wIdx, b, 2, 800) >= 2) {
            uint16_t rv = rd16(b);
            if (rv > 0) fv.resRaw = rv;
        }
    }

    if (fv.maxRaw <= fv.minRaw) {
        LOGW("FU[%d] 音量范围异常 (min=%d, max=%d)，禁用硬件音量", fv.unitId, fv.minRaw, fv.maxRaw);
        return false;
    }

    fv.supported = true;
    LOGI("硬件音量已就绪: FU[%d] wIndex=0x%04x (%s), master=%d chCount=%zu, "
         "范围 %.2f..%.2f dB (step %.2f dB), 当前 %.2f dB",
         fv.unitId, wIdx, matchedMode == 1 ? "uid<<8|acIf" : "acIf<<8|uid",
         fv.hasMaster ? 1 : 0, fv.channels.size(),
         fv.minDb(), fv.maxDb(), (double)fv.resRaw / 256.0, fv.curDb());
    return true;
}

bool uac_set_hardware_volume_db(libusb_device_handle *handle, UacDeviceInfo *info,
                                double db, bool mute) {
    if (!handle || !info || !info->hwVol.supported) return false;
    auto &fv = info->hwVol;
    const uint16_t wIdx = fu_windex(*info, fv.wIndexMode);
    const uint8_t setCurReq = (info->uacVersion >= 2) ? UAC2_CS_CUR : UAC1_SET_CUR;

    int16_t targetRaw = fv.minRaw;
    if (!mute) {
        double minD = fv.minDb();
        double maxD = fv.maxDb();
        if (db < minD) db = minD;
        if (db > maxD) db = maxD;
        int32_t raw32 = (int32_t)std::lround(db * 256.0);
        // 向下按步进对齐，保证实际响度绝不超过请求值（护耳安全优先）
        if (fv.resRaw > 1) {
            int32_t steps = (raw32 - (int32_t)fv.minRaw) / (int32_t)fv.resRaw;
            raw32 = (int32_t)fv.minRaw + steps * (int32_t)fv.resRaw;
        }
        if (raw32 < fv.minRaw) raw32 = fv.minRaw;
        if (raw32 > fv.maxRaw) raw32 = fv.maxRaw;
        targetRaw = (int16_t)raw32;
    }

    uint8_t data[2] = {
        (uint8_t)(targetRaw & 0xFF),
        (uint8_t)((targetRaw >> 8) & 0xFF),
    };

    bool allOk = true;
    if (fv.hasMaster) {
        uint16_t wVal = (uint16_t)((UAC_FU_VOLUME_CONTROL << 8) | 0);
        int r = libusb_control_transfer(handle, 0x21, setCurReq, wVal, wIdx, data, 2, 800);
        if (r < 0) {
            LOGW("硬件音量 SET_CUR(master) 失败: %s", libusb_strerror((libusb_error)r));
            allOk = false;
        }
    } else {
        for (int ch : fv.channels) {
            uint16_t wVal = (uint16_t)((UAC_FU_VOLUME_CONTROL << 8) | (ch & 0xFF));
            int r = libusb_control_transfer(handle, 0x21, setCurReq, wVal, wIdx, data, 2, 800);
            if (r < 0) {
                LOGW("硬件音量 SET_CUR(ch=%d) 失败: %s", ch, libusb_strerror((libusb_error)r));
                allOk = false;
            }
        }
    }

    if (allOk) {
        fv.curRaw = targetRaw;
        LOGI("硬件音量已设为 %.2f dB (raw=0x%04x, mute=%d)",
             fv.curDb(), (uint16_t)targetRaw, mute ? 1 : 0);
    }
    return allOk;
}
