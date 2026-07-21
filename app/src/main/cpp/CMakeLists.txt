cmake_minimum_required(VERSION 3.22.1)

project(garaho_rime LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# librime.so integration (design doc §3.3).
# Phase 1 ships a stub shared library so the JNI surface compiles & links;
# real librime linking (prebuilt .a + boost headers) is wired in Phase 4.

add_library(garaho_rime SHARED
        native-lib.cpp)

find_library(log-lib log)

target_link_libraries(garaho_rime
        ${log-lib})
