cmake_minimum_required(VERSION 3.13.0)

project("nekomp")

if (ANDROID)
    find_library(log-lib log)
    set(common-lib ${common-lib} ${log-lib})
else()
    SET(CMAKE_SHARED_LIBRARY_PREFIX "")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static -fPIC")
    include_directories("/mnt/d/Apps/jdk/include")
    include_directories("/mnt/d/Apps/jdk/include/win32")
endif ()

include("${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg/ffmpeg.cmake")
