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
if(ANDROID)
    target_link_libraries(tsuyomi_quickjs PRIVATE android log m)
elseif(NOT WIN32)
    target_link_libraries(tsuyomi_quickjs PRIVATE m)
endif()

option(TSUYOMI_QUICKJS_BUILD_NATIVE_TESTS "Build native QuickJS regression tests" OFF)

if(TSUYOMI_QUICKJS_BUILD_NATIVE_TESTS AND NOT ANDROID)
    enable_testing()

    add_executable(
        tsuyomi_quickjs_unicode_script_failure_test
        test/cpp/quickjs_unicode_script_failure_test.c
        quickjs-ng/libunicode.c
    )
    target_include_directories(tsuyomi_quickjs_unicode_script_failure_test PRIVATE quickjs-ng)
    target_compile_features(tsuyomi_quickjs_unicode_script_failure_test PRIVATE c_std_11)
    if(NOT WIN32)
        target_link_libraries(tsuyomi_quickjs_unicode_script_failure_test PRIVATE m)
        target_compile_definitions(tsuyomi_quickjs_unicode_script_failure_test PRIVATE _POSIX_C_SOURCE=200809L)
    endif()

    add_test(
        NAME QuickJsUnicodeScriptAllocationFailure
        COMMAND tsuyomi_quickjs_unicode_script_failure_test
    )
    set_tests_properties(QuickJsUnicodeScriptAllocationFailure PROPERTIES TIMEOUT 15)
endif()
