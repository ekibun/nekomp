#!/bin/bash
set -e

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

abi=$1

CONFIG_ARGS=(
  --enable-pic
  --enable-static
  --disable-shared
  --disable-programs
  --disable-encoders
  --disable-muxers
  --disable-network
  --disable-postproc
  --disable-avdevice
  --disable-protocols
  --disable-doc
  --disable-filters
  --disable-avfilter
  --disable-asm # TODO
  --prefix="$DIR/build/$abi/"
)
if [ "$abi" == "os" ]; then
  arch="$(uname -m)"
  if [ "$( which wslpath )" ]; then
    case $arch in
      "x86")
        CROSS_PREFIX=i686-w64-mingw32
        ;;
      "x86_64")
        CROSS_PREFIX=x86_64-w64-mingw32
        ;;
      *)
        exitUnsupport
    esac
    CONFIG_ARGS+=(
      --arch="$arch"
      --target-os=mingw32
      --cross-prefix="$CROSS_PREFIX-"
    )
  fi
else
  case "$OSTYPE" in
  darwin*)
    HOST_OS="darwin"
    ;;
  msys*)
    HOST_OS="windows"
    ;;
  *)
    HOST_OS="linux"
    ;;
  esac
  MIN_API=21
  ARCH_ROOT="$ANDROID_NDK_HOME/platforms/android-$MIN_API/arch-$abi"
  TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_OS-x86_64"
  case $abi in
    "arm")
      CC_PREFIX="$TOOLCHAIN/bin/armv7a-linux-androideabi$MIN_API"
      HOST=arm-linux-androideabi
      ;;
    "arm64")
      CC_PREFIX="$TOOLCHAIN/bin/aarch64-linux-android$MIN_API"
      HOST=aarch64-linux-android
      ;;
    "x86")
      CC_PREFIX="$TOOLCHAIN/bin/i686-linux-android$MIN_API"
      HOST=i686-linux-android
      ;;
    "x86_64")
      CC_PREFIX="$TOOLCHAIN/bin/x86_64-linux-android$MIN_API"
      HOST=x86_64-linux-android
      ;;
    *)
      echo "unsupported abi $abi"
      exit 1
      ;;
  esac
  CONFIG_ARGS+=(
    --arch="$abi"
    --target-os=android
    --enable-jni
    --enable-mediacodec
    --enable-cross-compile
    --cc="$CC_PREFIX-clang"
    --cxx="$CC_PREFIX-clang++"
    --cross-prefix="$TOOLCHAIN/bin/$HOST-"
    --extra-ldflags="-Wl,-rpath-link=$ARCH_ROOT/usr/lib"
  )
fi

echo "build ffmpeg for $abi"
echo "${CONFIG_ARGS[*]}"

buildDir=$DIR/build/_make/$abi

if [ -d "$buildDir" ]; then
  rm -r "$buildDir"
fi
mkdir -p "$buildDir"

cd "$buildDir"

"$DIR/ffmpeg/configure" "${CONFIG_ARGS[@]}"

if [ "$abi" != "os" ]; then
  sed -i "s/#define HAVE_INET_ATON 0/#define HAVE_INET_ATON 1/" config.h
  sed -i "s/#define getenv(x) NULL/\\/\\/ #define getenv(x) NULL/" config.h
fi

make -j8
make install
