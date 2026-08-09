# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

cmake_minimum_required(VERSION 3.22.1)
project(tsuyomi_quickjs LANGUAGES C CXX)

add_library(
    tsuyomi_quickjs
    SHARED
    tsuyomi_quickjs_jni.cpp
    quickjs-ng/dtoa.c
    quickjs-ng/libregexp.c
    quickjs-ng/libunicode.c
    quickjs-ng/quickjs.c
)

target_include_directories(tsuyomi_quickjs PRIVATE quickjs-ng)
target_compile_definitions(tsuyomi_quickjs PRIVATE _GNU_SOURCE)
target_compile_options(tsuyomi_quickjs PRIVATE -fvisibility=hidden)
target_link_libraries(tsuyomi_quickjs PRIVATE android log m)
