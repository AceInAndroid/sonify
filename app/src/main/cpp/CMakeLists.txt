cmake_minimum_required(VERSION 3.22.1)
project(hifiaudio C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# libusb 1.0.27 —— 直接以源码编译（tarball 不带 CMakeLists，手工列源文件）
# Android 下 linux_usbfs.h 用 !defined(__ANDROID__) 把 netlink 全跳过，
# 所以 linux_netlink.c 不必编译。
# ---------------------------------------------------------------------------
set(LIBUSB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libusb)

add_library(usb STATIC
        ${LIBUSB_DIR}/libusb/core.c
        ${LIBUSB_DIR}/libusb/descriptor.c
        ${LIBUSB_DIR}/libusb/hotplug.c
        ${LIBUSB_DIR}/libusb/io.c
        ${LIBUSB_DIR}/libusb/sync.c
        ${LIBUSB_DIR}/libusb/strerror.c
        ${LIBUSB_DIR}/libusb/os/linux_usbfs.c
        ${LIBUSB_DIR}/libusb/os/events_posix.c
        ${LIBUSB_DIR}/libusb/os/threads_posix.c
)

# android/config.h 是 libusb 官方给 Android 的配置头，
# 把它所在目录塞进 include path，让 libusbi.h 的 #include <config.h> 命中
target_include_directories(usb PRIVATE
        ${LIBUSB_DIR}/android
        ${LIBUSB_DIR}
        ${LIBUSB_DIR}/libusb
        ${LIBUSB_DIR}/libusb/os
)
target_compile_options(usb PRIVATE -fvisibility=hidden -pthread -Wno-unused-parameter)
target_link_libraries(usb PUBLIC log)

# ---------------------------------------------------------------------------
# 我们的音频库
# ---------------------------------------------------------------------------
add_library(hifiaudio SHARED
        uac_desc.cpp
        uac_stream.cpp
        test_tone.cpp
        pcm_ring_buffer.cpp
        jni_bridge.cpp
)

target_include_directories(hifiaudio PRIVATE
        ${LIBUSB_DIR}/libusb
)

target_compile_options(hifiaudio PRIVATE -Wall -Wextra -Wno-unused-parameter -fno-exceptions -fno-rtti)
target_link_libraries(hifiaudio PRIVATE usb android log)
