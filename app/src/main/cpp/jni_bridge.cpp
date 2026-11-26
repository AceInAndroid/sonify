// JNI 桥：把 UAC 能力与无锁 PCM 环形缓冲暴露给 Kotlin
//
// 分层原则（与 HiBy 一致）：
//   - 设备枚举 / 权限申请 → Kotlin 的 UsbManager
//   - fd 之后的一切      → native + libusb
// 注意：保留 gLock 保护设备生命周期，高频 PCM 写入通过 shared_ptr 副本脱离锁执行。
#include "pcm_ring_buffer.h"
#include "uac_stream.h"

#include <jni.h>
#include <unistd.h>
#include <memory>
#include <mutex>
#include <string>

extern AudioSource *create_test_tone(double freqHz, int durationSec, double amplitude);

namespace {

std::mutex                              gLock;
libusb_context                         *gCtx       = nullptr;
libusb_device_handle                   *gHandle    = nullptr;
UacDeviceInfo                           gInfo;
StreamAlt                               gAlt;
uint32_t                                gRate      = 0;
UacStream                               gStream;
AudioSource                            *gToneSrc   = nullptr;
std::shared_ptr<PcmRingBufferSource>    gPcmSrc;
int                                     gWrappedFd = -1;

// 保存当前软件音量与护耳上限，防止新建流/切采样率瞬间闪回满幅 1.0
double                                  gSwVolDb   = -45.0;
bool                                    gSwVolMute = false;
double                                  gSwMaxDb   = -24.0;

jstring to_jstring(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
}

bool ensure_ctx() {
    if (gCtx) return true;
    struct libusb_init_option opt[1];
    opt[0].option    = LIBUSB_OPTION_NO_DEVICE_DISCOVERY;
    opt[0].value.ival = 1;
    int r = libusb_init_context(&gCtx, opt, 1);
    if (r != 0) {
        LOGE("libusb_init_context 失败: %s", libusb_strerror((libusb_error)r));
        gCtx = nullptr;
        return false;
    }
    LOGI("libusb 初始化完成（NO_DEVICE_DISCOVERY）");
    return true;
}

// 挑选最匹配的 alt setting：
//   preferredSubslot == 0 时自动选最高位深；
//   preferredSubslot == 3/4 时优先精确匹配该容器位深（兼容某些不认 32bit 的便携 DAC）。
const StreamAlt *pick_alt(uint32_t rate, int preferredChannels = 2, int preferredSubslot = 0) {
    const StreamAlt *best = nullptr;
    int bestScore = -1;
    for (const auto &a : gInfo.alts) {
        if (!uac_supports_rate(a, rate)) continue;
        int score = 0;
        if ((int)a.fmt.channels == preferredChannels) score += 1000;
        else if (a.fmt.channels >= 2) score += 500;
        else score += (int)a.fmt.channels * 100;

        if (preferredSubslot > 0 && (int)a.fmt.subslotSize == preferredSubslot) {
            score += 200;
        }
        score += (int)a.fmt.subslotSize * 20;
        score += (int)a.fmt.bitResolution;

        if (!best || score > bestScore) {
            best = &a;
            bestScore = score;
        }
    }
    return best;
}

void stop_and_release() {
    gStream.stop();
    gStream.setSource(nullptr);
    if (gToneSrc) {
        delete gToneSrc;
        gToneSrc = nullptr;
    }
    gPcmSrc.reset();
}

}  // namespace

extern "C" {

// 用 UsbManager 授权后的 fd 打开设备，返回描述符解析结果（JSON）
JNIEXPORT jstring JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeOpen(JNIEnv *env, jclass, jint fd) {
    std::lock_guard<std::mutex> lk(gLock);

    if (gHandle) {
        stop_and_release();
        uac_release_interfaces(gHandle, gInfo);
        libusb_close(gHandle);
        gHandle = nullptr;
    }
    if (gWrappedFd >= 0) {
        close(gWrappedFd);
        gWrappedFd = -1;
    }
    if (fd < 0) return to_jstring(env, "ERROR: fd 无效");
    if (!ensure_ctx()) return to_jstring(env, "ERROR: libusb 初始化失败");

    int dupFd = dup(fd);
    if (dupFd < 0) {
        return to_jstring(env, "ERROR: dup(fd) 失败");
    }

    libusb_device_handle *h = nullptr;
    int r = libusb_wrap_sys_device(gCtx, (intptr_t)dupFd, &h);
    if (r != 0 || !h) {
        close(dupFd);
        char buf[160];
        snprintf(buf, sizeof(buf), "ERROR: libusb_wrap_sys_device 失败 (%s)",
                 libusb_strerror((libusb_error)r));
        LOGE("%s", buf);
        return to_jstring(env, buf);
    }

    gHandle    = h;
    gWrappedFd = dupFd;
    gInfo      = UacDeviceInfo{};
    gAlt       = StreamAlt{};
    gRate      = 0;

    std::string err;
    if (!uac_parse_device(h, &gInfo, &err)) {
        libusb_close(gHandle);
        gHandle = nullptr;
        close(dupFd);
        gWrappedFd = -1;
        return to_jstring(env, "ERROR: " + err);
    }

    std::string claimErr;
    if (!uac_claim_interfaces(h, gInfo, &claimErr)) {
        LOGW("libusb claim 未成功（%s），改用 Java 侧已 detach 的接口继续", claimErr.c_str());
    } else {
        LOGI("libusb 已自行 claim 全部音频接口");
    }

    uac_refresh_rates(h, &gInfo);

    // 探测 Feature Unit 硬件音量能力（必须在 claim 之后执行）
    uac_probe_hardware_volume(h, &gInfo);

    bool anyKnown = false;
    for (const auto &a : gInfo.alts) if (a.fmt.rateKnown) anyKnown = true;
    if (!anyKnown) uac_diagnose_clock(h, gInfo);

    return to_jstring(env, uac_info_to_json(gInfo));
}

JNIEXPORT jstring JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeSupportedRates(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lk(gLock);
    if (!gHandle) return to_jstring(env, "");
    std::string s;
    for (uint32_t r : gInfo.allSampleRates()) {
        if (!s.empty()) s += ",";
        s += std::to_string(r);
    }
    return to_jstring(env, s);
}

JNIEXPORT jboolean JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeSetRate(JNIEnv *, jclass, jint rate) {
    std::lock_guard<std::mutex> lk(gLock);
    if (!gHandle) return JNI_FALSE;

    if (gStream.running()) stop_and_release();

    const StreamAlt *a = pick_alt((uint32_t)rate);
    if (!a) {
        LOGE("没有 alt setting 支持 %d Hz", rate);
        return JNI_FALSE;
    }
    if (!uac_set_alt(gHandle, a->interfaceNumber, a->altSetting)) return JNI_FALSE;
    if (!uac_set_sample_rate(gHandle, gInfo, *a, (uint32_t)rate)) return JNI_FALSE;
    gAlt  = *a;
    gRate = (uint32_t)rate;
    return JNI_TRUE;
}

// M1 正弦波测试音（工程诊断页保留）
JNIEXPORT jboolean JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativePlayTone(JNIEnv *, jclass,
                                                       jdouble freq, jint seconds,
                                                       jint sampleRate,
                                                       jdouble amplitude) {
    std::lock_guard<std::mutex> lk(gLock);
    if (!gHandle) return JNI_FALSE;

    stop_and_release();

    const StreamAlt *a = pick_alt((uint32_t)sampleRate);
    if (!a) {
        LOGE("没有 alt setting 支持 %d Hz", sampleRate);
        return JNI_FALSE;
    }

    if (!uac_set_alt(gHandle, a->interfaceNumber, a->altSetting)) return JNI_FALSE;

    if (!uac_set_sample_rate(gHandle, gInfo, *a, (uint32_t)sampleRate)) {
        LOGW("SET_CUR 采样率 %d 失败，按自适应时钟处理：直接以该速率排包发送", sampleRate);
    }

    gAlt  = *a;
    gRate = (uint32_t)sampleRate;

    gToneSrc = create_test_tone(freq, seconds, amplitude);
    gStream.setSource(gToneSrc);
    gStream.setFeedbackControlEnabled(true);

    if (!gStream.start(gCtx, gHandle, *a, (uint32_t)sampleRate, a->fmt.subslotSize)) {
        delete gToneSrc;
        gToneSrc = nullptr;
        gStream.setSource(nullptr);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// M2：真实文件 PCM 流接口（供 UacAudioSink 调用）
// ---------------------------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeStartPcmStream(
        JNIEnv *, jclass,
        jint sampleRate,
        jint channels,
        jint preferredSubslot,
        jint bufferMs,
        jboolean fadeEnabled,
        jboolean feedbackEnabled,
        jboolean resetAlt) {
    std::lock_guard<std::mutex> lk(gLock);
    if (!gHandle) {
        LOGE("nativeStartPcmStream: 设备未打开");
        return JNI_FALSE;
    }

    stop_and_release();

    uint32_t reqRate = (uint32_t)sampleRate;
    const StreamAlt *a = pick_alt(reqRate, channels, preferredSubslot);
    if (!a) {
        uint32_t nearest = uac_nearest_rate(gInfo, reqRate);
        LOGW("设备不直接支持 %u Hz，回退到最近采样率 %u Hz", reqRate, nearest);
        reqRate = nearest;
        a = pick_alt(reqRate, channels, preferredSubslot);
        if (!a) {
            LOGE("找不到可用的 alt setting");
            return JNI_FALSE;
        }
    }

    if (resetAlt == JNI_TRUE) {
        uac_set_alt(gHandle, a->interfaceNumber, 0);
    }
    if (!uac_set_alt(gHandle, a->interfaceNumber, a->altSetting)) return JNI_FALSE;

    if (!uac_set_sample_rate(gHandle, gInfo, *a, reqRate)) {
        LOGW("SET_CUR 采样率 %u 失败，按自适应时钟继续", reqRate);
    }

    gAlt  = *a;
    gRate = reqRate;

    auto pcm = std::make_shared<PcmRingBufferSource>(bufferMs, fadeEnabled == JNI_TRUE);
    // 立即应用当前软件音量与底层护耳上限，防止起播第一帧满幅爆音
    pcm->setVolumeDb(gSwVolDb, gSwVolMute, gSwMaxDb);
    gPcmSrc = pcm;
    gStream.setSource(pcm.get());
    gStream.setFeedbackControlEnabled(feedbackEnabled == JNI_TRUE);

    if (!gStream.start(gCtx, gHandle, *a, reqRate, a->fmt.subslotSize)) {
        gStream.setSource(nullptr);
        gPcmSrc.reset();
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

// 零拷贝向环形缓冲写入 PCM 数据，返回实际消费的输入字节数
JNIEXPORT jint JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeWritePcm(
        JNIEnv *env, jclass,
        jobject directBuffer,
        jint offset,
        jint length,
        jint pcmEncoding,
        jint srcChannels) {
    if (!directBuffer || length <= 0 || offset < 0) return 0;

    std::shared_ptr<PcmRingBufferSource> src;
    {
        std::lock_guard<std::mutex> lk(gLock);
        src = gPcmSrc;
    }
    if (!src) return 0;

    auto *base = static_cast<const uint8_t *>(env->GetDirectBufferAddress(directBuffer));
    jlong cap  = env->GetDirectBufferCapacity(directBuffer);
    if (!base || (jlong)offset + (jlong)length > cap) return 0;

    size_t consumed = src->write(base + offset, (size_t)length, pcmEncoding, srcChannels);
    return (jint)consumed;
}

JNIEXPORT void JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeFlushPcm(JNIEnv *, jclass) {
    std::shared_ptr<PcmRingBufferSource> src;
    {
        std::lock_guard<std::mutex> lk(gLock);
        src = gPcmSrc;
    }
    if (src) src->flush();
}

JNIEXPORT void JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeSetPaused(JNIEnv *, jclass, jboolean paused) {
    std::shared_ptr<PcmRingBufferSource> src;
    {
        std::lock_guard<std::mutex> lk(gLock);
        src = gPcmSrc;
    }
    if (src) src->setPaused(paused == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeSetVolume(JNIEnv *, jclass, jfloat volume) {
    std::shared_ptr<PcmRingBufferSource> src;
    {
        std::lock_guard<std::mutex> lk(gLock);
        src = gPcmSrc;
    }
    if (src) src->setVolume(volume);
}

JNIEXPORT jboolean JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeHasHardwareVolume(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lk(gLock);
    return (gHandle && gInfo.hwVol.supported) ? JNI_TRUE : JNI_FALSE;
}

// 返回 [minDb, maxDb, curDb]
JNIEXPORT jboolean JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeGetHardwareVolumeRange(JNIEnv *env, jclass,
                                                                     jfloatArray outArr) {
    std::lock_guard<std::mutex> lk(gLock);
    if (!gHandle || !gInfo.hwVol.supported || !outArr || env->GetArrayLength(outArr) < 3) {
        return JNI_FALSE;
    }
    jfloat vals[3] = {
        (jfloat)gInfo.hwVol.minDb(),
        (jfloat)gInfo.hwVol.maxDb(),
        (jfloat)gInfo.hwVol.curDb(),
    };
    env->SetFloatArrayRegion(outArr, 0, 3, vals);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeSetHardwareVolumeDb(JNIEnv *, jclass,
                                                                  jfloat db, jboolean mute) {
    std::lock_guard<std::mutex> lk(gLock);
    if (!gHandle || !gInfo.hwVol.supported) return JNI_FALSE;
    return uac_set_hardware_volume_db(gHandle, &gInfo, (double)db, mute == JNI_TRUE)
           ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeSetSoftwareVolumeDb(JNIEnv *, jclass,
                                                                  jfloat db, jboolean mute,
                                                                  jfloat maxDb) {
    std::shared_ptr<PcmRingBufferSource> src;
    {
        std::lock_guard<std::mutex> lk(gLock);
        gSwVolDb   = (double)db;
        gSwVolMute = (mute == JNI_TRUE);
        gSwMaxDb   = (double)maxDb;
        src = gPcmSrc;
    }
    if (src) src->setVolumeDb((double)db, mute == JNI_TRUE, (double)maxDb);
}

JNIEXPORT void JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeSetEos(JNIEnv *, jclass) {
    std::shared_ptr<PcmRingBufferSource> src;
    {
        std::lock_guard<std::mutex> lk(gLock);
        src = gPcmSrc;
    }
    if (src) src->setEos();
}

// 批量返回 6 个状态数值（零字符串 GC）：
//   [0] consumedFrames  [1] bufferedFrames  [2] ringUnderruns
//   [3] inFlightFrames  [4] isRunning(0/1)  [5] isDrained(0/1)
JNIEXPORT jboolean JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeGetPcmState(JNIEnv *env, jclass, jlongArray outArr) {
    if (!outArr || env->GetArrayLength(outArr) < 6) return JNI_FALSE;

    std::shared_ptr<PcmRingBufferSource> src;
    uint64_t inFlight = 0;
    bool running = false;
    {
        std::lock_guard<std::mutex> lk(gLock);
        src      = gPcmSrc;
        running  = gStream.running();
        inFlight = gStream.inFlightFrames();
    }
    if (!src) return JNI_FALSE;

    jlong vals[6];
    vals[0] = (jlong)src->consumedFrames();
    vals[1] = (jlong)src->bufferedFrames();
    vals[2] = (jlong)src->ringUnderruns();
    vals[3] = (jlong)inFlight;
    vals[4] = running ? 1L : 0L;
    vals[5] = src->isDrained() ? 1L : 0L;
    env->SetLongArrayRegion(outArr, 0, 6, vals);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeStop(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lk(gLock);
    stop_and_release();
}

JNIEXPORT void JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeClose(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lk(gLock);
    stop_and_release();
    if (gHandle) {
        uac_release_interfaces(gHandle, gInfo);
        libusb_close(gHandle);
        gHandle = nullptr;
    }
    if (gWrappedFd >= 0) {
        close(gWrappedFd);
        gWrappedFd = -1;
    }
    gAlt = StreamAlt{};
    gRate = 0;
    LOGI("设备已关闭");
}

// 运行统计，给播放页输出徽章与工程诊断用
JNIEXPORT jstring JNICALL
Java_com_sonify_player_usb_UsbAudioNative_nativeStats(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lk(gLock);
    const char *mode = "idle";
    uint64_t ringUnderruns = 0;
    double bufferFillMs = 0.0;
    if (gPcmSrc) {
        mode = "pcm";
        ringUnderruns = gPcmSrc->ringUnderruns();
        bufferFillMs = gPcmSrc->bufferedMs();
    } else if (gToneSrc) {
        mode = "tone";
    }

    char buf[640];
    snprintf(buf, sizeof(buf),
             "{\"running\":%s,\"mode\":\"%s\",\"underruns\":%llu,\"ringUnderruns\":%llu,"
             "\"bufferFillMs\":%.1f,\"rate\":%u,\"effectiveRate\":%.1f,"
             "\"bits\":%u,\"subslot\":%u,\"channels\":%u,"
             "\"ep\":%d,\"feedbackEp\":%d,\"syncType\":%d,"
             "\"fbRaw\":%u,\"fbRate\":%.1f,"
             "\"hasHwVol\":%s,\"hwVolDb\":%.2f,\"swVolDb\":%.2f,"
             "\"buildTime\":\"%s %s\"}",
             gStream.running() ? "true" : "false",
             mode,
             (unsigned long long)gStream.underruns(),
             (unsigned long long)ringUnderruns,
             bufferFillMs,
             gRate,
             gStream.effectiveRate(),
             gAlt.fmt.bitResolution,
             gAlt.fmt.subslotSize,
             gAlt.fmt.channels,
             gAlt.dataEpAddr,
             gAlt.feedbackEpAddr,
             gAlt.syncType,
             gStream.feedbackRaw(),
             gStream.feedbackRate(),
             (gHandle && gInfo.hwVol.supported) ? "true" : "false",
             gInfo.hwVol.curDb(),
             gSwVolDb,
             __DATE__, __TIME__);
    return to_jstring(env, buf);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *, void *) {
    LOGI("libhifiaudio 已加载（构建于 %s %s）", __DATE__, __TIME__);
    return JNI_VERSION_1_6;
}

}  // extern "C"
