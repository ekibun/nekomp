cmake_minimum_required(VERSION 3.10.2)

if (ANDROID)
    set(FFMPEG_PATH "${CMAKE_CURRENT_LIST_DIR}/build/${CMAKE_ANDROID_ARCH}")
    set(ffmpeg-lib
            z)
else ()
    set(FFMPEG_PATH "${CMAKE_CURRENT_LIST_DIR}/build/os")
    set(ffmpeg-lib
            ws2_32
            bcrypt)
endif ()

add_library(ffmpeg SHARED
        ${CMAKE_CURRENT_LIST_DIR}/ffmpeg.cpp)

target_include_directories(ffmpeg PRIVATE "${FFMPEG_PATH}/include")

target_link_libraries(ffmpeg PRIVATE
        ${common-lib}
        "${FFMPEG_PATH}/lib/libavformat.a"
        "${FFMPEG_PATH}/lib/libavcodec.a"
        "${FFMPEG_PATH}/lib/libavutil.a"
        "${FFMPEG_PATH}/lib/libswresample.a"
        "${FFMPEG_PATH}/lib/libswscale.a"
        ${ffmpeg-lib}
        )
