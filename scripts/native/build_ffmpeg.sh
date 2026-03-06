#!/bin/bash

# Configuration
NDK_VERSION="r25c"
NDK_PATH="$HOME/android-tools/android-ndk-$NDK_VERSION"
FFMPEG_VERSION="6.0"
# Build in Linux home for speed and to avoid permission issues on Windows mount
BUILD_DIR="$HOME/ffmpeg-build"
# This is our destination on the Windows side
PROJECT_ROOT="/mnt/g/Android-app/Hyperplay/v2"
INSTALL_DIR="$PROJECT_ROOT/app/src/main/jniLibs"

# Architecture to build
ARCH="arm64-v8a"
TARGET="aarch64-linux-android"
API=21

echo "--- Starting FFmpeg Build for $ARCH ---"

# 1. Setup NDK in WSL if not present
if [ ! -d "$NDK_PATH" ]; then
    echo "NDK not found at $NDK_PATH. Please run the NDK setup commands first."
    exit 1
fi

# 2. Download FFmpeg source in home if not present
cd ~
if [ ! -d "ffmpeg-$FFMPEG_VERSION" ]; then
    echo "Downloading FFmpeg $FFMPEG_VERSION..."
    wget -q https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.bz2
    tar xjf ffmpeg-$FFMPEG_VERSION.tar.bz2
fi

cd "ffmpeg-$FFMPEG_VERSION"

# 3. Configure and Build
TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"

set -e

./configure \
    --prefix="$BUILD_DIR/$ARCH" \
    --enable-shared \
    --disable-static \
    --disable-doc \
    --disable-ffmpeg \
    --disable-ffplay \
    --disable-ffprobe \
    --disable-avdevice \
    --disable-symver \
    --disable-vulkan \
    --disable-hwaccels \
    --cross-prefix="$TOOLCHAIN/bin/$TARGET-" \
    --target-os=android \
    --arch=arm64 \
    --enable-cross-compile \
    --sysroot="$TOOLCHAIN/sysroot" \
    --extra-cflags="-Os -fPIC" \
    --cc="$TOOLCHAIN/bin/$TARGET$API-clang" \
    --cxx="$TOOLCHAIN/bin/$TARGET$API-clang++" \
    --nm="$TOOLCHAIN/bin/llvm-nm" \
    --strip="$TOOLCHAIN/bin/llvm-strip"

make clean
make -j2
make install

# 4. Copy to Project
echo "Copying to project..."
mkdir -p "$INSTALL_DIR/$ARCH"
cp "$BUILD_DIR/$ARCH/lib"/*.so "$INSTALL_DIR/$ARCH/"

mkdir -p "$PROJECT_ROOT/app/src/main/cpp/include"
cp -r "$BUILD_DIR/$ARCH/include/"* "$PROJECT_ROOT/app/src/main/cpp/include/"

echo "--- FFmpeg Build for $ARCH Complete ---"
echo "Libraries installed to: $INSTALL_DIR/$ARCH"
echo "Headers installed to: $PROJECT_ROOT/app/src/main/cpp/include"
