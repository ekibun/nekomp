cmake_minimum_required(VERSION 3.13.0)

set(FFMPEG_CONFIGURE_COMMAND
        ${CMAKE_CURRENT_LIST_DIR}/ffmpeg/configure
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
        --disable-asm
        )

if (ANDROID)
    set(FFMPEG_PATH "${CMAKE_CURRENT_LIST_DIR}/build/${CMAKE_ANDROID_ARCH}")
    set(ffmpeg-lib
            z)
    if (${CMAKE_HOST_SYSTEM_NAME} MATCHES "Windows")
        set(BASH_EXEC ${CMAKE_CURRENT_LIST_DIR}/../exec)
    endif()
    set(FFMPEG_CONFIGURE_COMMAND
            ${FFMPEG_CONFIGURE_COMMAND}
            --arch=${CMAKE_ANDROID_ARCH}
            --target-os=android
            --enable-jni
            --enable-mediacodec
            --enable-cross-compile
            --cc=${CMAKE_C_COMPILER}
            --cxx=${CMAKE_CXX_COMPILER}
            --extra-cflags="--target=${ANDROID_LLVM_TRIPLE}"
            --extra-ldflags=--target=${ANDROID_LLVM_TRIPLE}
            --cross-prefix=${ANDROID_TOOLCHAIN_PREFIX}
            )
else ()
    set(ffmpeg-lib
            ws2_32
            bcrypt)
    set(FFMPEG_CONFIGURE_COMMAND
            ${FFMPEG_CONFIGURE_COMMAND}
            --arch=x86_64
            --target-os=mingw32
            --cross-prefix=x86_64-w64-mingw32-
            )
endif ()

include(ExternalProject)
ExternalProject_Add(
        ffmpeg_lib
        PREFIX "ffmpeg_lib"
        SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/ffmpeg"
        BINARY_DIR "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
        INSTALL_DIR "${CMAKE_CURRENT_BINARY_DIR}"
        CONFIGURE_COMMAND ${BASH_EXEC} ${FFMPEG_CONFIGURE_COMMAND}
            --prefix=${CMAKE_CURRENT_BINARY_DIR}
        BUILD_COMMAND ${BASH_EXEC} make -j8
        INSTALL_COMMAND ${BASH_EXEC} make install
)

add_library(ffmpeg SHARED
        ${CMAKE_CURRENT_LIST_DIR}/ffmpeg.cpp)

add_dependencies(ffmpeg ffmpeg_lib)

target_link_directories(ffmpeg PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/lib")
target_include_directories(ffmpeg PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/include")

target_link_libraries(ffmpeg PRIVATE
        ${common-lib}
        avformat avcodec avutil swresample swscale
        ${ffmpeg-lib}
        )