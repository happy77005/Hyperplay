# Hyperplay v2: Hi-Res Bit-Perfect Audio Player

Hyperplay v2 is a state-of-the-art Android music player designed for audiophiles who demand the highest possible audio fidelity. It bypasses conventional Android audio limitations through a custom-built native engine, offering true Bit-Perfect playback and advanced DAC integration.

## ⬇️ Download
You can download the latest compiled APK from the [Releases](https://github.com/happy77005/Hyperplay/releases/tag/v1.0.0) page.

---

## 🚀 Core Philosophy: Bit-Perfect Audio

Standard Android playback often resamples audio to 48kHz, degrading high-resolution source material. Hyperplay v2 introduces a **Bit-Perfect** path that:
- Probes the source file's native sample rate.
- Dynamically re-opens the native audio stream to match the source (e.g., 96kHz, 192kHz).
- Requests **Exclusive Mode** via Oboe/AAudio to bypass the system mixer.
- Directs audio to external USB DACs with zero modification.

---

## 🛠 Architecture & Engineering

### 1. Dual-Engine Architecture
Hyperplay provides a flexible abstraction layer via the `IAudioEngine` interface, allowing the app to switch between two distinct backends:
- **Standard Engine (MediaPlayer)**: Optimized for low battery consumption and standard 16-bit/44.1kHz playback. Perfect for casual listening.
- **Native Hi-Res Engine (Oboe + FFmpeg)**: A C++ powerhouse that handles 24-bit/32-bit audio decoding and low-latency output.

### 2. Intelligent Device Discovery (`DacHelper`)
The app features a sophisticated discovery system that classifies output devices:
- **USB DAC Probing**: Automatically detects connected USB DACs and probes their hardware-supported sample rates (up to 384kHz and beyond).
- **Advanced Bluetooth Detection**: Uses Hidden API reflection to identify high-quality codecs (LDAC, aptX HD, aptX Adaptive) and display real-time technical info (96kHz / 24-bit).

### 3. Native Processing Layer
- **Oboe**: Google’s high-performance C++ library for low-latency audio.
- **FFmpeg**: Powers the decoding of various formats (FLAC, ALAC, WAV, DSD) with high-precision resampling via `libswresample`.
- **JNI Bridge**: A robust connectivity layer (`native-lib.cpp`) that enables real-time synchronization between the Kotlin service and the C++ engine.

---

## ✨ Key Features

- **Gapless Playback**: Seamlessly transitions between tracks without silence, powered by pre-loading logic in `MusicService`.
- **Chunked Library Scanning**: Efficiently indexes thousands of local songs across internal storage and SD cards without blocking the UI.
- **Audiophile Technical Info**: Real-time display of engine type, output sample rate, channel count, and bit-perfect status.
- **Built-in Equalizer & Speed Control**: A 5-band persistent equalizer and high-precision playback speed adjustment.
- **Modern UI**: Clean, responsive design with mini-player, queue management, and folder-based navigation.

---

## 📁 Project Structure

```text
app/src/main/
├── java/com/example/first/
│   ├── engine/          # Audio engine implementations (MediaPlayer, Native, Oboe)
│   ├── MainActivity.kt  # UI orchestration & library scanning
│   └── MusicService.kt  # Central playback & MediaSession hub
├── cpp/
│   ├── engine/          # C++ AudioEngine & FFmpegDecoder
│   ├── include/         # FFmpeg headers
│   └── external/oboe/   # Oboe source code
└── jniLibs/             # Pre-built FFmpeg binaries (.so) for arm64-v8a
```

---

## 🛠 Setup & Build Requirements

- **Android SDK**: 36 (Min SDK 26)
- **NDK**: 25.x or later
- **CMake**: 3.22.1+
- **FFmpeg**: Requires pre-built libraries placed in `app/src/main/jniLibs`.
- **Oboe**: Included as a Git submodule/folder in `external/`.

## 📜 License
Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

