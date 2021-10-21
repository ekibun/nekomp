cmake_minimum_required(VERSION 3.10.2)

set(FFMPEG_PATH "${CMAKE_CURRENT_LIST_DIR}/build/${CMAKE_ANDROID_ARCH}")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fPIC")
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
        z
        )
