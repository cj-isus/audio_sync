cmake_minimum_required(VERSION 3.22.1)

project("audioengine")

set(OBOE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/oboe)
add_subdirectory(${OBOE_DIR} ./oboe-bin)

add_library(audioengine SHARED
    audio_engine.cpp
    jni_bridge.cpp
)

target_include_directories(audioengine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${OBOE_DIR}/include
)

target_link_libraries(audioengine
    log
    oboe
)

target_compile_options(audioengine PRIVATE
    -Wall -Werror -O3 -ffast-math
)

target_compile_features(audioengine PRIVATE cxx_std_17)
