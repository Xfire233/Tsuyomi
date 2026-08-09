/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
#include <jni.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

extern "C" {
#include "quickjs.h"
}

namespace {

struct RuntimeHandle {
    JSRuntime* runtime = nullptr;
    JSContext* context = nullptr;
    std::atomic<bool> cancelled{false};
    std::atomic<int64_t> deadlineNanos{0};
};

int64_t monotonicNanos() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

int interruptHandler(JSRuntime*, void* opaque) {
    auto* handle = static_cast<RuntimeHandle*>(opaque);
    return handle->cancelled.load(std::memory_order_relaxed) ||
        monotonicNanos() >= handle->deadlineNanos.load(std::memory_order_relaxed);
}

void throwStable(JNIEnv* env, const char* code) {
    jclass exceptionClass = env->FindClass("org/tsuyomi/source/quickjsruntime/QuickJsNativeException");
    if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, code);
}

std::vector<uint8_t> byteArray(JNIEnv* env, jbyteArray value) {
    if (value == nullptr) return {};
    const jsize size = env->GetArrayLength(value);
    std::vector<uint8_t> result(static_cast<size_t>(size));
    if (size > 0) env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte*>(result.data()));
    return result;
}

jbyteArray toByteArray(JNIEnv* env, const char* value, size_t size) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(size));
    if (result != nullptr && size > 0) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(value));
    }
    return result;
}

void beginOperation(RuntimeHandle* handle, int wallTimeMillis) {
    handle->cancelled.store(false, std::memory_order_relaxed);
    const auto duration = static_cast<int64_t>(wallTimeMillis) * 1'000'000LL;
    handle->deadlineNanos.store(monotonicNanos() + duration, std::memory_order_relaxed);
    JS_ResetUncatchableError(handle->context);
}

void throwJsFailure(JNIEnv* env, RuntimeHandle* handle) {
    const bool cancelled = handle->cancelled.load(std::memory_order_relaxed);
    const bool timedOut = monotonicNanos() >= handle->deadlineNanos.load(std::memory_order_relaxed);
    JSValue exception = JS_GetException(handle->context);
    JSValue messageValue = JS_GetPropertyStr(handle->context, exception, "message");
    size_t messageLength = 0;
    const char* message = JS_ToCStringLen(handle->context, &messageLength, messageValue);
    const bool outOfMemory = message != nullptr &&
        std::string(message, messageLength).find("out of memory") != std::string::npos;
    if (message != nullptr) JS_FreeCString(handle->context, message);
    JS_FreeValue(handle->context, messageValue);
    JS_FreeValue(handle->context, exception);
    if (cancelled) throwStable(env, "CANCELLED");
    else if (timedOut) throwStable(env, "EXECUTION_LIMIT");
    else if (outOfMemory) throwStable(env, "MEMORY_LIMIT");
    else throwStable(env, "JS_EXCEPTION");
}

bool runPendingJobs(JNIEnv* env, RuntimeHandle* handle) {
    while (JS_IsJobPending(handle->runtime)) {
        JSContext* jobContext = nullptr;
        if (JS_ExecutePendingJob(handle->runtime, &jobContext) < 0) {
            throwJsFailure(env, handle);
            return false;
        }
    }
    return true;
}

RuntimeHandle* fromHandle(jlong value) {
    return reinterpret_cast<RuntimeHandle*>(static_cast<intptr_t>(value));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_create(
    JNIEnv* env,
    jclass,
    jlong memoryLimitBytes
) {
    auto handle = std::make_unique<RuntimeHandle>();
    handle->runtime = JS_NewRuntime();
    if (handle->runtime == nullptr) {
        throwStable(env, "NATIVE_UNAVAILABLE");
        return 0;
    }
    JS_SetMemoryLimit(handle->runtime, static_cast<size_t>(memoryLimitBytes));
    JS_SetInterruptHandler(handle->runtime, interruptHandler, handle.get());
    handle->context = JS_NewContext(handle->runtime);
    if (handle->context == nullptr) {
        JS_FreeRuntime(handle->runtime);
        throwStable(env, "MEMORY_LIMIT");
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle.release()));
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_evaluateModule(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jbyteArray sourceValue,
    jbyteArray filenameValue,
    jint wallTimeMillis
) {
    auto* handle = fromHandle(nativeHandle);
    if (handle == nullptr || handle->context == nullptr) {
        throwStable(env, "CLOSED");
        return;
    }
    auto source = byteArray(env, sourceValue);
    auto filename = byteArray(env, filenameValue);
    source.push_back(0);
    filename.push_back(0);
    beginOperation(handle, wallTimeMillis);
    JSValue result = JS_Eval(
        handle->context,
        reinterpret_cast<const char*>(source.data()),
        source.size() - 1,
        reinterpret_cast<const char*>(filename.data()),
        JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_BACKTRACE_BARRIER
    );
    if (JS_IsException(result)) {
        JS_FreeValue(handle->context, result);
        throwJsFailure(env, handle);
        return;
    }
    JS_FreeValue(handle->context, result);
    runPendingJobs(env, handle);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_callJson(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jbyteArray functionNameValue,
    jbyteArray argumentsJsonValue,
    jint wallTimeMillis
) {
    auto* handle = fromHandle(nativeHandle);
    if (handle == nullptr || handle->context == nullptr) {
        throwStable(env, "CLOSED");
        return nullptr;
    }
    auto functionName = byteArray(env, functionNameValue);
    auto argumentsJson = byteArray(env, argumentsJsonValue);
    functionName.push_back(0);
    argumentsJson.push_back(0);
    beginOperation(handle, wallTimeMillis);

    JSValue global = JS_GetGlobalObject(handle->context);
    JSValue extension = JS_GetPropertyStr(handle->context, global, "tsuyomiExtension");
    JSValue function = JS_GetPropertyStr(
        handle->context,
        extension,
        reinterpret_cast<const char*>(functionName.data())
    );
    if (!JS_IsFunction(handle->context, function)) {
        JS_FreeValue(handle->context, function);
        JS_FreeValue(handle->context, extension);
        JS_FreeValue(handle->context, global);
        throwStable(env, "MISSING_FUNCTION");
        return nullptr;
    }

    JSValue arguments = JS_ParseJSON(
        handle->context,
        reinterpret_cast<const char*>(argumentsJson.data()),
        argumentsJson.size() - 1,
        "<host-arguments>"
    );
    if (JS_IsException(arguments) || !JS_IsArray(arguments)) {
        JS_FreeValue(handle->context, arguments);
        JS_FreeValue(handle->context, function);
        JS_FreeValue(handle->context, extension);
        JS_FreeValue(handle->context, global);
        if (JS_HasException(handle->context)) JS_GetException(handle->context);
        throwStable(env, "INVALID_ARGUMENTS");
        return nullptr;
    }

    JSValue lengthValue = JS_GetPropertyStr(handle->context, arguments, "length");
    int64_t argumentCount = 0;
    const int lengthStatus = JS_ToInt64(handle->context, &argumentCount, lengthValue);
    JS_FreeValue(handle->context, lengthValue);
    if (lengthStatus < 0 || argumentCount < 0 || argumentCount > 32) {
        JS_FreeValue(handle->context, arguments);
        JS_FreeValue(handle->context, function);
        JS_FreeValue(handle->context, extension);
        JS_FreeValue(handle->context, global);
        throwStable(env, "INVALID_ARGUMENTS");
        return nullptr;
    }

    std::vector<JSValue> values(static_cast<size_t>(argumentCount));
    for (uint32_t index = 0; index < static_cast<uint32_t>(argumentCount); ++index) {
        values[index] = JS_GetPropertyUint32(handle->context, arguments, index);
    }
    JSValue result = JS_Call(
        handle->context,
        function,
        extension,
        static_cast<int>(argumentCount),
        values.data()
    );
    for (JSValue value : values) JS_FreeValue(handle->context, value);
    JS_FreeValue(handle->context, arguments);
    JS_FreeValue(handle->context, function);
    JS_FreeValue(handle->context, extension);
    JS_FreeValue(handle->context, global);
    if (JS_IsException(result)) {
        JS_FreeValue(handle->context, result);
        throwJsFailure(env, handle);
        return nullptr;
    }
    if (!runPendingJobs(env, handle)) {
        JS_FreeValue(handle->context, result);
        return nullptr;
    }

    JSValue json = JS_JSONStringify(handle->context, result, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(handle->context, result);
    if (JS_IsException(json) || JS_IsUndefined(json)) {
        JS_FreeValue(handle->context, json);
        if (JS_HasException(handle->context)) throwJsFailure(env, handle);
        else throwStable(env, "NON_JSON_RESULT");
        return nullptr;
    }
    size_t outputSize = 0;
    const char* output = JS_ToCStringLen(handle->context, &outputSize, json);
    JS_FreeValue(handle->context, json);
    if (output == nullptr) {
        throwJsFailure(env, handle);
        return nullptr;
    }
    jbyteArray response = toByteArray(env, output, outputSize);
    JS_FreeCString(handle->context, output);
    return response;
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_cancel(
    JNIEnv*,
    jclass,
    jlong nativeHandle
) {
    auto* handle = fromHandle(nativeHandle);
    if (handle != nullptr) handle->cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_close(
    JNIEnv*,
    jclass,
    jlong nativeHandle
) {
    auto* handle = fromHandle(nativeHandle);
    if (handle == nullptr) return;
    handle->cancelled.store(true, std::memory_order_relaxed);
    if (handle->context != nullptr) JS_FreeContext(handle->context);
    if (handle->runtime != nullptr) JS_FreeRuntime(handle->runtime);
    delete handle;
}
