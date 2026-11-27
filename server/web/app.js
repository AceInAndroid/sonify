(() => {
  "use strict";

  const I18N = {
    zh: {
      pageTitle: "Sonify — Android Bit-Perfect USB 独占 HiFi 播放器",
      "nav.showcase": "界面预览",
      "nav.features": "核心引擎",
      "nav.hardware": "实测硬件",
      "nav.download": "下载",
      "nav.getApp": "免费下载",
      "hero.badge": "v1.0.0 正式版 · Android 7.0+ · ARM64 / ARMv7",
      "hero.title": '绕过重采样，<br /><span class="text-gradient">听见母带本色。</span>',
      "hero.subtitle":
        "专为本地无损与外接 USB DAC 打造的开源 Android HiFi 播放器。内置自研 C++17 / libusb 用户态 UAC1 & UAC2 独占驱动，直通最高 384 kHz / 32-bit Bit-Perfect PCM，融合 Spotify 极简深色美学、旋转黑胶唱片与流体光晕同步歌词。",
      "hero.ctaDownload": "下载 v1.0.0 正式版",
      "hero.ctaGithub": "查看 GitHub 源码",
      "hero.m1": "最高直通采样率",
      "hero.m2": "异步反馈时钟锁定",
      "hero.m3": "硬件级防炸耳保护",
      "hero.vinylPlaying": "黑胶旋转中",
      "hero.vinylPaused": "黑胶已暂停",
      "showcase.eyebrow": "SPOTIFY DARK × VINYL AESTHETICS",
      "showcase.title": "像黑胶一样沉浸，像仪表一样精准",
      "showcase.desc":
        "点击下方标签浏览全部 7 个核心实机界面（含 MOONRIVER 3 实机 USB 独占链路与硬件仪表）。切换右上角「中文 / EN」，手机画面同步热切换对应语言实拍。",
      "tabs.now_playing": "黑胶播放页",
      "tabs.audio_output": "音频输出与链路",
      "tabs.usb_dac": "USB DAC 仪表",
      "tabs.lyrics": "流体光晕歌词",
      "tabs.library": "无损音乐库",
      "tabs.global_search": "跨源全局搜索",
      "tabs.settings": "设置与双语",
      "features.eyebrow": "AUDIOPHILE ARCHITECTURE",
      "features.title": "从 C++ 底层驱动到指尖交互，全程无损",
      "features.desc": "拒绝套壳与黑盒音效，每一项特性都围绕真实发烧听音场景打磨。",
      "f1.kicker": "C++17 + LIBUSB 1.0.27",
      "f1.title": "真正绕过 Android AudioFlinger 的 USB 独占引擎",
      "f1.desc":
        "通过 UsbManager 授权与 claimInterface(force = true) 剥离内核 snd-usb-audio，经 libusb_wrap_sys_device() 直接在用户态按 125 µs 微帧精准排包，并由 16.16 定点异步 Feedback 端点闭环锁定 DAC 晶振。",
      "f2.kicker": "4-LAYER EAR GUARD",
      "f2.title": "硬件音量与四重防炸耳护耳",
      "f2.desc":
        "优先调用 UAC Feature Unit 硬件衰减器，保持数字 PCM 100% 位精确直通；前后台接管物理音量键，默认 60% 硬上限与 25% 安全起步音量，守护高敏耳塞与听力。",
      "f2.simLabel": "实时护耳模拟（上限 60%）",
      "f2.safe": "● 安全听音区 · Bit-Perfect 硬件衰减",
      "f2.clamped": "● 已触发 60% 护耳硬钳位 · 拒绝意外炸耳",
      "f3.title": "流体光晕与五级同步歌词",
      "f3.desc":
        "本地 LRC（UTF-8/GB18030 防乱码、逐字增强时间戳）→ 内嵌标签 → Subsonic → LRCLIB 在线五级瀑布匹配，搭配 60fps 逐句流光扫色与多色流体氛围光晕。",
      "f4.title": "Subsonic 多服与离线原盘",
      "f4.desc":
        "完整兼容 Navidrome / Subsonic / Airsonic / Gonic / Jellyfin，密码经 Android Keystore (AES-256-GCM) 加密保存，Wi-Fi 默认 format=raw 原盘直通与整单离线下载。",
      "f5.title": "WiFi 局域网无损传歌",
      "f5.desc":
        "手机扫码或电脑浏览器拖拽整张专辑文件夹直传，内置三层 SHA-256 防重复校验与零 OOM 流式落盘，上传完毕秒入音乐库。",
      "f6.title": "简繁互通搜索与双语热切换",
      "f6.desc":
        "跨本地曲库、歌单、Subsonic 与 .lrc 歌词搜歌，内置简繁归一化与规格搜索（FLAC 96k）；中英双语 1 帧即时切换，不断开 USB DAC 播放。",
      "hw.eyebrow": "REAL-DEVICE VERIFIED",
      "hw.title": "真机与外置 DAC 实测数据",
      "hw.desc":
        "所有版本均经过物理 Android 10 (API 29) 机型与水月雨 MOONRIVER 3 (UAC2 High-Speed) 实机门禁验收，保证长时间息屏播放稳定零欠载。",
      "hw.clock": "异步反馈实测",
      "hw.vol": "硬件音量单元",
      "hw.compat": "系统兼容范围",
      "dl.eyebrow": "OFFICIAL RELEASE v1.0.0",
      "dl.title": "立即下载 Sonify v1.0.0",
      "dl.desc": "全部安装包均为官方 V1+V2+V3 正式签名，支持高速直连下载与 SHA-256 完整性校验。",
      "dl.recommended": "推荐 · 绝大多数手机",
      "dl.arm64Desc": "适配绝大多数现代 64 位 Android 手机与 HiFi 国砖播放器，体积更轻、性能最佳。",
      "dl.universalBadge": "全架构通用",
      "dl.uniDesc": "同时内置 64 位与 32 位原生音频驱动库；如果不确定设备处理器架构，直接选择此包即可。",
      "dl.armv7Badge": "32 位旧机型",
      "dl.armv7Desc": "专为老款 32 位 Android 手机与早期 HiFi 便携播放器编译的精简安装包。",
      "dl.directBtn": "高速直连下载",
      "dl.ghMirror": "GitHub 镜像下载",
      "dl.copy": "复制",
      "dl.copied": "已复制 SHA-256 校验值",
      "dl.checksumNote": "完整校验文件与版本更新日志：",
      "dl.releasePage": "GitHub Release v1.0.0",
      "dl.changelog": "更新日志 CHANGELOG",
      "footer.tagline": "开源 Android Bit-Perfect USB 独占 HiFi 播放器 · 专注纯粹无损听音体验。",
      "footer.license": "遵循 Sonify 非商用开源许可协议 v1.0：允许个人免费使用、学习与社区贡献，未经作者书面授权严禁任何形式商用。",
      "footer.project": "开源项目",
      "footer.releases": "全部版本",
      "footer.changelog": "更新日志",
      "footer.docs": "文档与协议",
      "footer.licenseLink": "非商用许可协议",
      "footer.i18n": "多语言适配标准",
      "footer.issues": "问题反馈",
    },
    en: {
      pageTitle: "Sonify — Bit-Perfect USB Exclusive HiFi Player for Android",
      "nav.showcase": "Screens",
      "nav.features": "Engine",
      "nav.hardware": "Hardware",
      "nav.download": "Download",
      "nav.getApp": "Download",
      "hero.badge": "v1.0.0 Official · Android 7.0+ · ARM64 / ARMv7",
      "hero.title": 'Bypass Resampling.<br /><span class="text-gradient">Hear Every Bit.</span>',
      "hero.subtitle":
        "An open-source, local-first Android HiFi player with a custom C++17 / libusb userspace UAC1 & UAC2 exclusive driver. Deliver true Bit-Perfect PCM up to 384 kHz / 32-bit directly to your USB DAC—wrapped in a Spotify-dark vinyl UI with fluid aurora lyrics.",
      "hero.ctaDownload": "Download v1.0.0 APK",
      "hero.ctaGithub": "View on GitHub",
      "hero.m1": "Max Native PCM Rate",
      "hero.m2": "Async Feedback Lock",
      "hero.m3": "Hardware Ear Protection",
      "hero.vinylPlaying": "Vinyl Spinning",
      "hero.vinylPaused": "Vinyl Paused",
      "showcase.eyebrow": "SPOTIFY DARK × VINYL AESTHETICS",
      "showcase.title": "Immersive like Vinyl. Precise like a Lab.",
      "showcase.desc":
        "Explore all 7 real-device screens—including live MOONRIVER 3 USB Exclusive signal path & telemetry. Toggle Chinese / EN in the top bar to hot-swap every phone mockup.",
      "tabs.now_playing": "Now Playing",
      "tabs.audio_output": "Signal Path",
      "tabs.usb_dac": "USB DAC",
      "tabs.lyrics": "Aurora Lyrics",
      "tabs.library": "Music Library",
      "tabs.global_search": "Global Search",
      "tabs.settings": "Settings & i18n",
      "features.eyebrow": "AUDIOPHILE ARCHITECTURE",
      "features.title": "Bit-Perfect from C++ Driver to Fingertip",
      "features.desc": "No hidden resamplers, no closed-source DSP gimmicks—engineered for serious local & DAC listening.",
      "f1.kicker": "C++17 + LIBUSB 1.0.27",
      "f1.title": "True AudioFlinger-Bypassing USB Exclusive Engine",
      "f1.desc":
        "Detaches the kernel snd-usb-audio driver via Android UsbManager and streams 125 µs High-Speed isochronous OUT URBs through libusb_wrap_sys_device(), locked to your DAC crystal via 16.16 fixed-point async feedback.",
      "f2.kicker": "4-LAYER EAR GUARD",
      "f2.title": "Hardware Volume & 4-Layer Ear Protection",
      "f2.desc":
        "Controls the DAC's UAC Feature Unit hardware attenuator directly so PCM stays 100% bit-exact. Hijacks physical volume keys foreground & background with a 60% hard ceiling and 25% safe startup attenuation.",
      "f2.simLabel": "Interactive Ear Guard (60% Ceiling)",
      "f2.safe": "● Safe Listening Zone · Bit-Perfect HW Attenuator",
      "f2.clamped": "● Clamped at 60% Ceiling · Blast Prevented",
      "f3.title": "Fluid Aurora & 5-Tier Synced Lyrics",
      "f3.desc":
        "5-tier waterfall (Local LRC with UTF-8/GB18030 & word timestamps → Embedded → Subsonic → LRCLIB Online) rendered with 60fps Progressive Sweep highlight and multi-color aurora glow.",
      "f4.title": "Subsonic Multi-Server & Offline Lossless",
      "f4.desc":
        "Works with Navidrome, Subsonic, Airsonic, Gonic & Jellyfin. Credentials encrypted via Android Keystore (AES-256-GCM), raw format=raw streaming & offline album/playlist downloads.",
      "f5.title": "WiFi LAN Lossless Transfer",
      "f5.desc":
        "Drag-and-drop full album folders from desktop or mobile browsers with QR pairing, 3-layer SHA-256 deduplication, zero-OOM streaming writes, and instant library indexing.",
      "f6.title": "Cross-Source Search & Instant Bilingual UI",
      "f6.desc":
        "Unified search across local tracks, playlists, Subsonic (search3), audio specs (FLAC 96k) & .lrc lyrics with CJK normalization. Switch ZH/EN in 1 frame with zero playback interruption.",
      "hw.eyebrow": "REAL-DEVICE VERIFIED",
      "hw.title": "Measured on Real Android & USB DAC Hardware",
      "hw.desc":
        "Every release passes physical QA on Android 10 (API 29) paired with Moondrop MOONRIVER 3 (UAC2 High-Speed) for glitch-free screen-off listening.",
      "hw.clock": "Measured Async Feedback",
      "hw.vol": "Hardware Volume Unit",
      "hw.compat": "Compatibility",
      "dl.eyebrow": "OFFICIAL RELEASE v1.0.0",
      "dl.title": "Download Sonify v1.0.0",
      "dl.desc": "Official V1+V2+V3 signed APKs with high-speed direct HTTPS downloads and SHA-256 verification.",
      "dl.recommended": "Recommended · Most Phones",
      "dl.arm64Desc": "Built for modern 64-bit Android smartphones and HiFi DAPs—smallest footprint, maximum performance.",
      "dl.universalBadge": "All ARM Devices",
      "dl.uniDesc": "Includes both 64-bit and 32-bit native audio libraries. Pick this if you're unsure of your device's ABI.",
      "dl.armv7Badge": "Legacy 32-bit",
      "dl.armv7Desc": "Lightweight 32-bit build tailored for older Android devices and classic portable audio players.",
      "dl.directBtn": "Direct HTTPS Download",
      "dl.ghMirror": "GitHub Release Mirror",
      "dl.copy": "Copy",
      "dl.copied": "SHA-256 checksum copied!",
      "dl.checksumNote": "Integrity checksums & release notes:",
      "dl.releasePage": "GitHub Release v1.0.0",
      "dl.changelog": "CHANGELOG",
      "footer.tagline": "Open-Source Bit-Perfect USB Exclusive HiFi Player for Android.",
      "footer.license":
        "Released under the Sonify Non-Commercial Source-Available License v1.0: free for personal use, study & contributions; commercial use strictly prohibited without written authorization.",
      "footer.project": "Project",
      "footer.releases": "Releases",
      "footer.changelog": "Changelog",
      "footer.docs": "Docs & License",
      "footer.licenseLink": "Non-Commercial License",
      "footer.i18n": "i18n Standard",
      "footer.issues": "Report an Issue",
    },
  };

  const SCREENS = {
    now_playing: {
      zh: {
        tag: "01 / 07 · NOW PLAYING",
        title: "黑胶全屏播放页 & USB 硬件音量",
        body: "纯 Canvas 60fps 绘制旋转黑胶与静止 45° 高光，自动提取专辑主色渲染暗调氛围渐变；实时展示「FLAC → ● USB 独占 44.1k/32b」信号胶囊、常驻 UAC 硬件衰减音量条（-42.4 dB）与 60% 护耳上限。",
        bullets: [
          "FLAC / WAV 原生采样率与实际 USB 协商位深实时透明展示",
          "底部直接控制 DAC 硬件音量并标注 60% 护耳硬上限，保持 PCM 位精确直通",
          "轻触唱片进入 Zen Vinyl 纯享黑胶，支持真·无缝播放与 5ms 防爆音淡变",
        ],
      },
      en: {
        tag: "01 / 07 · NOW PLAYING",
        title: "Spinning Vinyl & Hardware Volume Bar",
        body: "Pure Canvas 60fps vinyl disc with fixed 45° specular sheen and album-derived ambient glow. Shows the live FLAC → USB Exclusive signal badge, embedded UAC hardware attenuator bar (-42.4 dB), and 60% Ear Guard ceiling.",
        bullets: [
          "Transparent source format to negotiated USB DAC output badge",
          "Direct UAC hardware volume slider with 60% Ear Guard ceiling indicator",
          "One-tap Zen Vinyl mode, true gapless playback & 5ms click-free fades",
        ],
      },
    },
    audio_output: {
      zh: {
        tag: "02 / 07 · SIGNAL PATH",
        title: "音频输出与完整信号链路面板",
        body: "点击播放页信号胶囊或底部设备名即可拉起实时链路诊断抽屉：清晰确认「Bit-Perfect USB 独占已锁定（绕过 Android 混音）」，逐层透明呈现音频源、UAC2 输出协商、实测晶振反馈频率与缓冲水位，并支持一键在 USB DAC 与本机输出之间无缝切换。",
        bullets: [
          "实时显示音频源（FLAC 44.1kHz / 16bit）→ 输出协商（UAC2 44100Hz / 32bit 4B）",
          "异步时钟反馈实测锁定 44099.6 Hz · 缓冲 176 ms · 零欠载",
          "一键在已接入的 MOONRIVER 3 独占通道与本机扬声器/蓝牙间无爆音切换",
        ],
      },
      en: {
        tag: "02 / 07 · SIGNAL PATH",
        title: "Audio Output & Bit-Perfect Signal Path",
        body: "Tap the signal pill or output name on Now Playing to inspect the live end-to-end audio chain: confirms Bit-Perfect USB Exclusive lock (Android mixer bypassed) with source spec, UAC2 negotiated format, measured DAC clock feedback, and one-tap output switching.",
        bullets: [
          "End-to-end telemetry: Source FLAC → UAC2 44100Hz / 32bit (4B) negotiation",
          "Live async clock lock at 44099.6 Hz with 172–176 ms ring buffer & 0 underruns",
          "Instant click-free switching between MOONRIVER 3 exclusive mode & device audio",
        ],
      },
    },
    usb_dac: {
      zh: {
        tag: "03 / 07 · USB DAC TELEMETRY",
        title: "USB DAC 独占仪表与实测晶振监控",
        body: "接入小尾巴后实时呈现「● USB 独占输出运行中」状态卡片与 2×2 工程仪表：协商采样率（44100 Hz）、位深容器（32b / 4B）、异步 Feedback 实测频率（44099.6 Hz）与缓冲/欠载计数（172ms · 0/224），下方完整列出 VID/PID 与 440Hz/1kHz 工程测试音。",
        bullets: [
          "实拍水月雨 MOONRIVER 3（VID 0x35D8 / PID 0x43DA）UAC2 高速直通运行状态",
          "16.16 定点异步 Feedback 闭环锁频，长时间息屏播放稳定零欠载",
          "支持一键断开独占或拔出自动平滑回退至本机扬声器/耳机",
        ],
      },
      en: {
        tag: "03 / 07 · USB DAC TELEMETRY",
        title: "USB DAC Dashboard & Live Crystal Telemetry",
        body: "Real-time dashboard showing USB Exclusive Streaming Active on Moondrop MOONRIVER 3 with a 2×2 engineering telemetry grid: negotiated sample rate (44100 Hz), bit depth/slot (32b / 4B), measured async feedback (44099.6 Hz), and buffer/underruns (172ms · 0/224).",
        bullets: [
          "Live capture on Moondrop MOONRIVER 3 (VID 0x35D8 / PID 0x43DA, UAC2 High-Speed)",
          "Closed-loop 16.16 async feedback keeps crystal lock within < 10 ppm, 0 underruns",
          "One-tap Disconnect or automatic fallback to device speaker / Bluetooth on unplug",
        ],
      },
    },
    lyrics: {
      zh: {
        tag: "04 / 07 · AURORA LYRICS",
        title: "流体多色光晕 & 60fps 流光扫色歌词",
        body: "兼容 Android 10+ 的边到边流体多色光晕背景，逐句 60fps 流光扫色高亮与距离景深羽化；支持本地 LRC、内嵌标签、Subsonic 与 LRCLIB 在线五级瀑布自动匹配与实时偏移微调。",
        bullets: [
          "60fps 逐句流光扫色 / 柔光纯白 / 主题强调色 / 玻璃聚焦条",
          "自动识别 UTF-8 / UTF-16 / GB18030，支持增强型逐字时间戳与译文",
          "底部所见即所得样式面板：字体、字号、行距与对齐实时预览",
        ],
      },
      en: {
        tag: "04 / 07 · AURORA LYRICS",
        title: "Fluid Aurora & 60fps Progressive Sweep Lyrics",
        body: "Edge-to-edge multi-color fluid aurora backdrop smooth on Android 10+, paired with 60fps Progressive Sweep lyric highlight, depth-of-field feathering, and a 5-tier lyrics engine with LRCLIB online matching.",
        bullets: [
          "4 highlight styles: Progressive Sweep, Pure Glow, Accent Tint, Capsule",
          "Auto-decodes UTF-8/UTF-16/GB18030, enhanced word timestamps & translations",
          "Live translucent style customizer for font, size, spacing & alignment",
        ],
      },
    },
    library: {
      zh: {
        tag: "05 / 07 · MUSIC LIBRARY",
        title: "毫秒级无损音乐库 & 多源融合",
        body: "自研二进制无损头解析器秒扫 FLAC / WAV / MP3 / M4A / OGG / OPUS，支持专辑封面网格、艺人、文件夹、自定义歌单与 Subsonic 远程曲库一键切换，首页保留「继续上次播放」断点卡片。",
        bullets: [
          "本地歌单与 Subsonic 远端歌单全生命周期同步与 2×2 四宫格封面",
          "多选批量加入歌单、持久化多维排序与整张专辑离线原盘下载",
          "WiFi 局域网传歌秒入本地曲库，支持三层 SHA-256 防重",
        ],
      },
      en: {
        tag: "05 / 07 · MUSIC LIBRARY",
        title: "Instant Lossless Library & Multi-Source Hub",
        body: "Custom binary header parser scans FLAC, WAV, MP3, M4A, OGG & OPUS in milliseconds. Switch effortlessly between local albums, artists, folders, playlists, and self-hosted Subsonic servers.",
        bullets: [
          "Local & remote Subsonic playlists with 2×2 collage & custom covers",
          "Multi-select batch operations, persistent sorting & offline raw downloads",
          "Resume-last-session card & instant WiFi transfer indexing",
        ],
      },
    },
    global_search: {
      zh: {
        tag: "06 / 07 · GLOBAL SEARCH",
        title: "全屏跨源全局搜索 & 歌词搜歌",
        body: "一框聚合检索本地歌曲、专辑、艺人、自建歌单、Subsonic 远程曲库（search3）以及本地/缓存 .lrc 歌词片段，点击歌词命中行直达对应秒数起播。",
        bullets: [
          "内置简繁互通归一化（搜「周杰伦 叶惠美」秒中「周杰倫 葉惠美」）",
          "支持音频规格筛选检索（如 FLAC 96k）与快捷发现卡片",
          "暖金命中词高亮 + 最近 12 条持久化搜索历史",
        ],
      },
      en: {
        tag: "06 / 07 · GLOBAL SEARCH",
        title: "Full-Screen Cross-Source & Lyrics Search",
        body: "Unified instant search spanning local songs, albums, artists, playlists, remote Subsonic servers (search3), and local/cached .lrc lyrics—jump directly to the matched timestamp.",
        bullets: [
          "Built-in Simplified ↔ Traditional Chinese normalization",
          "Search by audio spec (e.g. FLAC 96k) & Quick Discovery cards",
          "Warm-gold match highlighting with persistent recent history",
        ],
      },
    },
    settings: {
      zh: {
        tag: "07 / 07 · SETTINGS & I18N",
        title: "极简分组设置 & 零中断双语热切换",
        body: "覆盖 USB 策略、四重护耳上限、深色底色与强调色、沉浸模式、歌词样式、WiFi 传歌、Subsonic 与应用内安全更新。切换「跟随系统 / 简体中文 / English」1 帧生效，绝不打断 USB DAC 播放。",
        bullets: [
          "多套 Spotify 深色画布与黑胶暖金 / 翡翠绿等强调色自由搭配",
          "独立 :crash 进程 3 秒自愈重启 + 脱敏诊断日志",
          "隐私优先设计：应用内一键关闭匿名统计，零个人文件路径采集",
        ],
      },
      en: {
        tag: "07 / 07 · SETTINGS & I18N",
        title: "Minimalist Settings & Zero-Interruption i18n",
        body: "Fine-tune USB policy, 4-layer ear guard, dark canvas & accent themes, immersive mode, lyrics styling, WiFi transfer, Subsonic, and SHA-256 verified updates. Switch English / Simplified Chinese in 1 frame without interrupting USB audio.",
        bullets: [
          "Multiple dark canvas presets & warm-gold / emerald accent themes",
          "Isolated :crash recovery process with 3s auto-restart & sanitized logs",
          "Privacy-first with 1-tap analytics opt-out & zero personal data collection",
        ],
      },
    },
  };

  let currentLang = (navigator.language || "").toLowerCase().startsWith("zh") ? "zh" : "en";
  let currentScreen = "now_playing";
  let isVinylSpinning = true;
  let toastTimer = null;

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.from(document.querySelectorAll(sel));

  function showToast(msg) {
    const el = $("#toast");
    if (!el) return;
    el.textContent = msg;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 2200);
  }

  function applyLanguage(lang) {
    currentLang = lang === "en" ? "en" : "zh";
    document.documentElement.lang = currentLang === "zh" ? "zh-CN" : "en";
    const dict = I18N[currentLang];
    document.title = dict.pageTitle;

    $$("[data-i18n]").forEach((el) => {
      const key = el.getAttribute("data-i18n");
      if (dict[key] !== undefined) el.textContent = dict[key];
    });

    $$("[data-i18n-html]").forEach((el) => {
      const key = el.getAttribute("data-i18n-html");
      if (dict[key] !== undefined) el.innerHTML = dict[key];
    });

    $$(".lang-btn").forEach((btn) => {
      btn.classList.toggle("active", btn.getAttribute("data-lang") === currentLang);
    });

    // Update vinyl toggle label
    updateVinylToggleLabel();

    // Swap Hero screenshot to matching language
    const heroImg = $("#heroScreenshot");
    if (heroImg) {
      heroImg.src = `/assets/screenshots/${currentLang}/now_playing.webp`;
    }

    // Re-render current showcase screen in chosen language
    renderScreen(currentScreen);

    // Re-render ear guard simulator status text
    updateEarSimulator();
  }

  function renderScreen(screenKey) {
    if (!SCREENS[screenKey]) return;
    currentScreen = screenKey;
    const data = SCREENS[screenKey][currentLang];
    const stage = $("#showcaseStage");
    const img = $("#showcaseImg");

    if (stage) stage.setAttribute("data-theme", screenKey);
    if (img) {
      const nextSrc = `/assets/screenshots/${currentLang}/${screenKey}.webp`;
      if (!img.src.endsWith(nextSrc)) {
        img.style.opacity = "0.25";
        img.onload = () => {
          img.style.opacity = "1";
        };
        img.src = nextSrc;
      }
      img.alt = data.title;
    }

    $("#screenTag").textContent = data.tag;
    $("#screenTitle").textContent = data.title;
    $("#screenBody").textContent = data.body;

    const ul = $("#screenBullets");
    ul.innerHTML = "";
    data.bullets.forEach((b) => {
      const li = document.createElement("li");
      li.textContent = b;
      ul.appendChild(li);
    });

    $$(".tab-pill").forEach((btn) => {
      const active = btn.getAttribute("data-screen") === screenKey;
      btn.classList.toggle("active", active);
      btn.setAttribute("aria-selected", active ? "true" : "false");
    });

    $$(".thumb-dot").forEach((dot) => {
      dot.classList.toggle("active", dot.getAttribute("data-screen") === screenKey);
    });
  }

  function updateVinylToggleLabel() {
    const dict = I18N[currentLang];
    const text = $("#vinylToggleText");
    if (text) {
      text.textContent = isVinylSpinning ? dict["hero.vinylPlaying"] : dict["hero.vinylPaused"];
    }
  }

  function updateEarSimulator() {
    const slider = $("#earSlider");
    const readout = $("#earSimReadout");
    const status = $("#earSimStatus");
    if (!slider || !readout || !status) return;

    const raw = parseInt(slider.value, 10) || 0;
    const clamped = raw > 60;
    const effective = Math.min(raw, 60);
    const db = effective === 0 ? "-∞ dB" : `${(-60 + (effective / 100) * 60).toFixed(1)} dB`;

    readout.textContent = `${effective}% · ${db}`;
    slider.classList.toggle("clamped", clamped);
    status.className = clamped ? "status-clamped" : "status-safe";
    status.textContent = I18N[currentLang][clamped ? "f2.clamped" : "f2.safe"];
  }

  // Bind Language Switch
  $$(".lang-btn").forEach((btn) => {
    btn.addEventListener("click", () => applyLanguage(btn.getAttribute("data-lang")));
  });

  // Bind Showcase Tabs & Dots
  $$(".tab-pill, .thumb-dot").forEach((el) => {
    el.addEventListener("click", () => renderScreen(el.getAttribute("data-screen")));
  });

  // Bind Vinyl Play/Pause Toggle
  const vinylToggle = $("#vinylToggle");
  if (vinylToggle) {
    vinylToggle.addEventListener("click", () => {
      isVinylSpinning = !isVinylSpinning;
      const vinyl = $("#heroVinyl");
      if (vinyl) {
        vinyl.classList.toggle("spinning", isVinylSpinning);
        vinyl.classList.toggle("paused", !isVinylSpinning);
      }
      $("#vinylIconPause").style.display = isVinylSpinning ? "block" : "none";
      $("#vinylIconPlay").style.display = isVinylSpinning ? "none" : "block";
      updateVinylToggleLabel();
    });
  }

  // Bind Ear Guard Slider
  const earSlider = $("#earSlider");
  if (earSlider) {
    earSlider.addEventListener("input", () => {
      updateEarSimulator();
    });
  }

  // Bind SHA-256 Copy Buttons
  $$(".sha-box").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const sha = btn.getAttribute("data-sha");
      if (!sha) return;
      try {
        await navigator.clipboard.writeText(sha);
      } catch (_) {
        const ta = document.createElement("textarea");
        ta.value = sha;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand("copy");
        document.body.removeChild(ta);
      }
      showToast(I18N[currentLang]["dl.copied"]);
    });
  });

  // Fetch Live Release Version from /api/v1/update/latest (graceful fallback if unavailable)
  fetch("/api/v1/update/latest", { cache: "no-store" })
    .then((r) => (r.ok ? r.json() : null))
    .then((data) => {
      if (!data || !data.versionName) return;
      const ver = `v${data.versionName}`;
      I18N.zh["hero.badge"] = `${ver} 正式版 · Android 7.0+ · ARM64 / ARMv7`;
      I18N.en["hero.badge"] = `${ver} Official · Android 7.0+ · ARM64 / ARMv7`;
      const badge = $("#liveVersionBadge");
      if (badge) badge.textContent = I18N[currentLang]["hero.badge"];
    })
    .catch(() => {});

  // Initial Render
  applyLanguage(currentLang);
})();
