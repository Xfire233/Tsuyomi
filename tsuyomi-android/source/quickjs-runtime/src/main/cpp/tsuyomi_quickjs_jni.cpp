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
#include <mutex>
#include <unordered_map>
#include <algorithm>

#include <cerrno>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
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

std::mutex handlesMutex;
std::unordered_map<jlong, std::shared_ptr<RuntimeHandle>> handles;
std::atomic<jlong> nextHandle{1};

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
    // Publication requests are serialized, but may enter JNI from different worker threads.
    JS_UpdateStackTop(handle->runtime);
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
    const std::string failure = message == nullptr ? "" : std::string(message, messageLength);
    const bool outOfMemory = failure.find("out of memory") != std::string::npos;
    const std::string prefix = "TSUYOMI_PUB_ERROR:";
    const std::string publicationCode = failure.compare(0, prefix.size(), prefix) == 0
        ? failure.substr(prefix.size())
        : "";
    const bool recognizedPublicationCode =
        publicationCode == "MALFORMED_INPUT" || publicationCode == "PROTECTED_CONTENT" ||
        publicationCode == "UNSUPPORTED_FORMAT" || publicationCode == "UNSUPPORTED_COMPRESSION" ||
        publicationCode == "PRINT_REPLICA" || publicationCode == "INPUT_LIMIT" ||
        publicationCode == "SECTION_LIMIT" || publicationCode == "TEXT_LIMIT" ||
        publicationCode == "RESOURCE_LIMIT" || publicationCode == "OUTPUT_LIMIT" ||
        publicationCode == "MISSING_SECTION" || publicationCode == "MISSING_RESOURCE" ||
        publicationCode == "INVALID_RESOURCE" || publicationCode == "EXTERNAL_RESOURCE_DENIED" ||
        publicationCode == "MALFORMED_XML" || publicationCode == "MALFORMED_TOC" ||
        publicationCode == "FIXED_LAYOUT";
    if (message != nullptr) JS_FreeCString(handle->context, message);
    JS_FreeValue(handle->context, messageValue);
    JS_FreeValue(handle->context, exception);
    if (cancelled) throwStable(env, "CANCELLED");
    else if (timedOut) throwStable(env, "EXECUTION_LIMIT");
    else if (outOfMemory) throwStable(env, "MEMORY_LIMIT");
    else if (recognizedPublicationCode) throwStable(env, publicationCode.c_str());
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

std::shared_ptr<RuntimeHandle> fromHandle(jlong value) {
    std::lock_guard<std::mutex> lock(handlesMutex);
    const auto handle = handles.find(value);
    return handle == handles.end() ? nullptr : handle->second;
}

jlong registerHandle(std::shared_ptr<RuntimeHandle> handle) {
    const jlong identifier = nextHandle.fetch_add(1, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(handlesMutex);
    handles.emplace(identifier, std::move(handle));
    return identifier;
}

std::shared_ptr<RuntimeHandle> takeHandle(jlong value) {
    std::lock_guard<std::mutex> lock(handlesMutex);
    const auto handle = handles.find(value);
    if (handle == handles.end()) return nullptr;
    auto result = std::move(handle->second);
    handles.erase(handle);
    return result;
}

constexpr size_t kMobiMaxInputBytes = 32 * 1024 * 1024;
constexpr size_t kMobiMaxJsonOutputBytes = 4 * 1024 * 1024;
constexpr size_t kMobiMaxResourceBytes = 8 * 1024 * 1024;

bool checkBookOutputFd(JNIEnv* env, int fd) {
    const int flags = fcntl(fd, F_GETFL);
    if (flags < 0 || (flags & O_ACCMODE) != O_WRONLY) {
        throwStable(env, "OUTPUT_FD_MODE");
        return false;
    }
    struct stat status {};
    if (fstat(fd, &status) != 0 || !S_ISFIFO(status.st_mode)) {
        throwStable(env, "OUTPUT_FD_METADATA");
        return false;
    }
    return true;
}

bool readBookInputFd(JNIEnv* env, int fd, size_t maximumBytes, std::vector<uint8_t>* result) {
    const int flags = fcntl(fd, F_GETFL);
    if (flags < 0 || (flags & O_ACCMODE) != O_RDONLY) {
        throwStable(env, "INPUT_FD_MODE");
        return false;
    }
    struct stat status {};
    if (fstat(fd, &status) != 0 || (!S_ISREG(status.st_mode) && !S_ISFIFO(status.st_mode))) {
        throwStable(env, "INPUT_FD_METADATA");
        return false;
    }
    if (S_ISREG(status.st_mode)) {
        if (status.st_size < 0 || static_cast<uint64_t>(status.st_size) > maximumBytes) {
            throwStable(env, "INPUT_LIMIT");
            return false;
        }
        const size_t expected = static_cast<size_t>(status.st_size);
        result->resize(expected);
        size_t offset = 0;
        while (offset < expected) {
            const ssize_t count = read(fd, result->data() + offset, expected - offset);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) {
                throwStable(env, "INPUT_FD_IO");
                return false;
            }
            offset += static_cast<size_t>(count);
        }
        uint8_t extra = 0;
        ssize_t count;
        do {
            count = read(fd, &extra, 1);
        } while (count < 0 && errno == EINTR);
        if (count > 0) {
            throwStable(env, "INPUT_LIMIT");
            return false;
        }
        if (count < 0) {
            throwStable(env, "INPUT_FD_IO");
            return false;
        }
        return true;
    }

    uint8_t chunk[16 * 1024];
    while (true) {
        ssize_t count;
        do {
            count = read(fd, chunk, sizeof(chunk));
        } while (count < 0 && errno == EINTR);
        if (count < 0) {
            throwStable(env, "INPUT_FD_IO");
            return false;
        }
        if (count == 0) return true;
        const size_t size = static_cast<size_t>(count);
        if (size > maximumBytes || result->size() > maximumBytes - size) {
            throwStable(env, "INPUT_LIMIT");
            return false;
        }
        result->insert(result->end(), chunk, chunk + size);
    }
}

bool writeBookOutputFd(JNIEnv* env, RuntimeHandle* handle, int fd, const uint8_t* bytes, size_t size) {
    size_t offset = 0;
    while (offset < size) {
        if (handle->cancelled.load(std::memory_order_relaxed)) {
            throwStable(env, "CANCELLED");
            return false;
        }
        const ssize_t count = write(fd, bytes + offset, size - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) {
            throwStable(env, "OUTPUT_FD_IO");
            return false;
        }
        offset += static_cast<size_t>(count);
    }
    return true;
}

bool callBookFunctionToFd(
    JNIEnv* env,
    RuntimeHandle* handle,
    const std::vector<uint8_t>& functionName,
    const std::vector<uint8_t>& argumentsJson,
    const uint8_t* inputBytes,
    size_t inputSize,
    bool hasInput,
    int outputFd,
    size_t maximumOutputBytes,
    bool binaryResult
) {
    if (functionName.empty() || std::find(functionName.begin(), functionName.end(), 0) != functionName.end() ||
        maximumOutputBytes == 0 || maximumOutputBytes > (binaryResult ? kMobiMaxResourceBytes : kMobiMaxJsonOutputBytes)) {
        throwStable(env, "INVALID_ARGUMENTS");
        return false;
    }
    if (!checkBookOutputFd(env, outputFd)) return false;

    JSContext* context = handle->context;
    JSValue global = JS_GetGlobalObject(context);
    JSValue bridge = JS_GetPropertyStr(context, global, "tsuyomiMobi");
    std::vector<uint8_t> terminatedName(functionName);
    terminatedName.push_back(0);
    JSValue function = JS_GetPropertyStr(context, bridge, reinterpret_cast<const char*>(terminatedName.data()));
    if (!JS_IsFunction(context, function)) {
        JS_FreeValue(context, function);
        JS_FreeValue(context, bridge);
        JS_FreeValue(context, global);
        throwStable(env, "MISSING_FUNCTION");
        return false;
    }

    JSValue arguments = JS_ParseJSON(
        context,
        reinterpret_cast<const char*>(argumentsJson.data()),
        argumentsJson.size() - 1,
        "<book-host-arguments>"
    );
    if (JS_IsException(arguments) || !JS_IsArray(arguments)) {
        JS_FreeValue(context, arguments);
        JS_FreeValue(context, function);
        JS_FreeValue(context, bridge);
        JS_FreeValue(context, global);
        if (JS_HasException(context)) JS_FreeValue(context, JS_GetException(context));
        throwStable(env, "INVALID_ARGUMENTS");
        return false;
    }
    JSValue lengthValue = JS_GetPropertyStr(context, arguments, "length");
    int64_t argumentCount = 0;
    const int lengthStatus = JS_ToInt64(context, &argumentCount, lengthValue);
    JS_FreeValue(context, lengthValue);
    if (lengthStatus < 0 || argumentCount < 0 || argumentCount > (hasInput ? 31 : 32)) {
        JS_FreeValue(context, arguments);
        JS_FreeValue(context, function);
        JS_FreeValue(context, bridge);
        JS_FreeValue(context, global);
        throwStable(env, "INVALID_ARGUMENTS");
        return false;
    }

    std::vector<JSValue> values(static_cast<size_t>(argumentCount) + (hasInput ? 1 : 0), JS_UNDEFINED);
    for (uint32_t index = 0; index < static_cast<uint32_t>(argumentCount); ++index) {
        values[index] = JS_GetPropertyUint32(context, arguments, index);
    }
    bool argumentsValid = true;
    if (hasInput) {
        JSValue bytes = JS_NewUint8ArrayCopy(context, inputBytes, inputSize);
        if (JS_IsException(bytes)) {
            argumentsValid = false;
        } else {
            values[static_cast<size_t>(argumentCount)] = bytes;
        }
    }
    JS_FreeValue(context, arguments);
    JSValue result = JS_UNDEFINED;
    if (argumentsValid) {
        result = JS_Call(context, function, bridge, static_cast<int>(values.size()), values.data());
    } else {
        throwJsFailure(env, handle);
    }
    for (JSValue value : values) JS_FreeValue(context, value);
    JS_FreeValue(context, function);
    JS_FreeValue(context, bridge);
    JS_FreeValue(context, global);
    if (!argumentsValid) return false;
    if (JS_IsException(result)) {
        JS_FreeValue(context, result);
        throwJsFailure(env, handle);
        return false;
    }
    if (!runPendingJobs(env, handle)) {
        JS_FreeValue(context, result);
        return false;
    }

    if (binaryResult) {
        if (JS_GetTypedArrayType(result) != JS_TYPED_ARRAY_UINT8) {
            JS_FreeValue(context, result);
            throwStable(env, "INVALID_BINARY_RESULT");
            return false;
        }
        size_t byteOffset = 0;
        size_t byteLength = 0;
        size_t bytesPerElement = 0;
        JSValue buffer = JS_GetTypedArrayBuffer(context, result, &byteOffset, &byteLength, &bytesPerElement);
        if (JS_IsException(buffer)) {
            JS_FreeValue(context, buffer);
            JS_FreeValue(context, result);
            throwJsFailure(env, handle);
            return false;
        }
        size_t bufferSize = 0;
        uint8_t* data = JS_GetArrayBuffer(context, &bufferSize, buffer);
        const bool valid = data != nullptr && bytesPerElement == 1 && byteOffset <= bufferSize &&
            byteLength <= bufferSize - byteOffset && byteLength > 0;
        if (!valid) {
            JS_FreeValue(context, buffer);
            JS_FreeValue(context, result);
            throwStable(env, "INVALID_BINARY_RESULT");
            return false;
        }
        if (byteLength > maximumOutputBytes) {
            JS_FreeValue(context, buffer);
            JS_FreeValue(context, result);
            throwStable(env, "OUTPUT_LIMIT");
            return false;
        }
        const bool written = writeBookOutputFd(env, handle, outputFd, data + byteOffset, byteLength);
        JS_FreeValue(context, buffer);
        JS_FreeValue(context, result);
        return written;
    }

    JSValue json = JS_JSONStringify(context, result, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(context, result);
    if (JS_IsException(json) || JS_IsUndefined(json)) {
        JS_FreeValue(context, json);
        if (JS_HasException(context)) throwJsFailure(env, handle);
        else throwStable(env, "NON_JSON_RESULT");
        return false;
    }
    size_t outputSize = 0;
    const char* output = JS_ToCStringLen(context, &outputSize, json);
    JS_FreeValue(context, json);
    if (output == nullptr) {
        throwJsFailure(env, handle);
        return false;
    }
    if (outputSize > maximumOutputBytes) {
        JS_FreeCString(context, output);
        throwStable(env, "OUTPUT_LIMIT");
        return false;
    }
    const bool written = writeBookOutputFd(env, handle, outputFd, reinterpret_cast<const uint8_t*>(output), outputSize);
    JS_FreeCString(context, output);
    return written;
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_create(
    JNIEnv* env,
    jclass,
    jlong memoryLimitBytes
) {
    auto handle = std::make_shared<RuntimeHandle>();
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
    return registerHandle(std::move(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_prepareOperation(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jint wallTimeMillis
) {
    auto handle = fromHandle(nativeHandle);
    if (handle == nullptr || handle->context == nullptr) {
        throwStable(env, "CLOSED");
        return;
    }
    beginOperation(handle.get(), wallTimeMillis);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_evaluateModule(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jbyteArray sourceValue,
    jbyteArray filenameValue
) {
    auto handle = fromHandle(nativeHandle);
    if (handle == nullptr || handle->context == nullptr) {
        throwStable(env, "CLOSED");
        return;
    }
    auto source = byteArray(env, sourceValue);
    auto filename = byteArray(env, filenameValue);
    source.push_back(0);
    filename.push_back(0);
    JSValue result = JS_Eval(
        handle->context,
        reinterpret_cast<const char*>(source.data()),
        source.size() - 1,
        reinterpret_cast<const char*>(filename.data()),
        JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_BACKTRACE_BARRIER
    );
    if (JS_IsException(result)) {
        JS_FreeValue(handle->context, result);
        throwJsFailure(env, handle.get());
        return;
    }
    JS_FreeValue(handle->context, result);
    runPendingJobs(env, handle.get());
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_callJson(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jbyteArray functionNameValue,
    jbyteArray argumentsJsonValue
) {
    auto handle = fromHandle(nativeHandle);
    if (handle == nullptr || handle->context == nullptr) {
        throwStable(env, "CLOSED");
        return nullptr;
    }
    auto functionName = byteArray(env, functionNameValue);
    auto argumentsJson = byteArray(env, argumentsJsonValue);
    functionName.push_back(0);
    argumentsJson.push_back(0);

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
        if (JS_HasException(handle->context)) JS_FreeValue(handle->context, JS_GetException(handle->context));
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
        throwJsFailure(env, handle.get());
        return nullptr;
    }
    if (!runPendingJobs(env, handle.get())) {
        JS_FreeValue(handle->context, result);
        return nullptr;
    }

    JSValue json = JS_JSONStringify(handle->context, result, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(handle->context, result);
    if (JS_IsException(json) || JS_IsUndefined(json)) {
        JS_FreeValue(handle->context, json);
        if (JS_HasException(handle->context)) throwJsFailure(env, handle.get());
        else throwStable(env, "NON_JSON_RESULT");
        return nullptr;
    }
    size_t outputSize = 0;
    const char* output = JS_ToCStringLen(handle->context, &outputSize, json);
    JS_FreeValue(handle->context, json);
    if (output == nullptr) {
        throwJsFailure(env, handle.get());
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
    auto handle = fromHandle(nativeHandle);
    if (handle != nullptr) handle->cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_close(
    JNIEnv*,
    jclass,
    jlong nativeHandle
) {
    auto handle = takeHandle(nativeHandle);
    if (handle == nullptr) return;
    handle->cancelled.store(true, std::memory_order_relaxed);
    if (handle->context != nullptr) {
        JS_FreeContext(handle->context);
        handle->context = nullptr;
    }
    if (handle->runtime != nullptr) {
        JS_FreeRuntime(handle->runtime);
        handle->runtime = nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_makeInputNonBlocking(
    JNIEnv*, jclass, jint fd
) {
    const int flags = fcntl(fd, F_GETFL);
    if (flags == -1) return errno;
    return fcntl(fd, F_SETFL, flags | O_NONBLOCK) == -1 ? errno : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_createBookRuntime(
    JNIEnv* env,
    jclass
) {
    auto handle = std::make_shared<RuntimeHandle>();
    handle->runtime = JS_NewRuntime();
    if (handle->runtime == nullptr) {
        throwStable(env, "NATIVE_UNAVAILABLE");
        return 0;
    }
    JS_SetMemoryLimit(handle->runtime, 64ULL * 1024 * 1024);
    JS_SetInterruptHandler(handle->runtime, interruptHandler, handle.get());
    handle->context = JS_NewContext(handle->runtime);
    if (handle->context == nullptr) {
        JS_FreeRuntime(handle->runtime);
        throwStable(env, "MEMORY_LIMIT");
        return 0;
    }
    return registerHandle(std::move(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_prepareBookOperation(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jint wallTimeMillis
) {
    Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_prepareOperation(
        env, nullptr, nativeHandle, wallTimeMillis);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_evaluateBookModule(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jbyteArray source,
    jbyteArray filename
) {
    Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_evaluateModule(
        env, nullptr, nativeHandle, source, filename);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_callBookJsonToFd(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jbyteArray functionNameValue,
    jbyteArray argumentsJsonValue,
    jint outputFd,
    jint maxOutputBytes
) {
    auto handle = fromHandle(nativeHandle);
    if (handle == nullptr || handle->context == nullptr) {
        throwStable(env, "CLOSED");
        return;
    }
    if (maxOutputBytes < 1 || static_cast<size_t>(maxOutputBytes) > kMobiMaxJsonOutputBytes) {
        throwStable(env, "INVALID_ARGUMENTS");
        return;
    }
    auto functionName = byteArray(env, functionNameValue);
    auto argumentsJson = byteArray(env, argumentsJsonValue);
    if (argumentsJson.size() > 8 * 1024) {
        throwStable(env, "INVALID_ARGUMENTS");
        return;
    }
    argumentsJson.push_back(0);
    callBookFunctionToFd(
        env, handle.get(), functionName, argumentsJson, nullptr, 0, false,
        outputFd, static_cast<size_t>(maxOutputBytes), false);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_callBookInputToFd(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jbyteArray functionNameValue,
    jint inputFd,
    jint outputFd,
    jint maxInputBytes,
    jint maxOutputBytes
) {
    auto handle = fromHandle(nativeHandle);
    if (handle == nullptr || handle->context == nullptr) {
        throwStable(env, "CLOSED");
        return;
    }
    if (maxInputBytes < 1 || static_cast<size_t>(maxInputBytes) > kMobiMaxInputBytes ||
        maxOutputBytes < 1 || static_cast<size_t>(maxOutputBytes) > kMobiMaxJsonOutputBytes) {
        throwStable(env, "INVALID_ARGUMENTS");
        return;
    }
    if (!checkBookOutputFd(env, outputFd)) return;
    std::vector<uint8_t> input;
    if (!readBookInputFd(env, inputFd, static_cast<size_t>(maxInputBytes), &input)) return;
    auto functionName = byteArray(env, functionNameValue);
    const std::vector<uint8_t> argumentsJson{'[', ']', 0};
    callBookFunctionToFd(
        env, handle.get(), functionName, argumentsJson, input.data(), input.size(), true,
        outputFd, static_cast<size_t>(maxOutputBytes), false);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_callBookBinaryToFd(
    JNIEnv* env,
    jclass,
    jlong nativeHandle,
    jbyteArray functionNameValue,
    jbyteArray resourceIdValue,
    jint outputFd,
    jint maxOutputBytes
) {
    auto handle = fromHandle(nativeHandle);
    if (handle == nullptr || handle->context == nullptr) {
        throwStable(env, "CLOSED");
        return;
    }
    if (maxOutputBytes < 1 || static_cast<size_t>(maxOutputBytes) > kMobiMaxResourceBytes) {
        throwStable(env, "INVALID_ARGUMENTS");
        return;
    }
    auto functionName = byteArray(env, functionNameValue);
    auto resourceId = byteArray(env, resourceIdValue);
    if (resourceId.empty() || resourceId.size() > 32) {
        throwStable(env, "INVALID_ARGUMENTS");
        return;
    }
    for (uint8_t character : resourceId) {
        if (!((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') ||
              (character >= '0' && character <= '9') || character == '_' || character == '-')) {
            throwStable(env, "INVALID_ARGUMENTS");
            return;
        }
    }
    if (!checkBookOutputFd(env, outputFd)) return;
    std::vector<uint8_t> argumentsJson;
    argumentsJson.reserve(resourceId.size() + 5);
    argumentsJson.push_back('[');
    argumentsJson.push_back('"');
    argumentsJson.insert(argumentsJson.end(), resourceId.begin(), resourceId.end());
    argumentsJson.push_back('"');
    argumentsJson.push_back(']');
    argumentsJson.push_back(0);
    callBookFunctionToFd(
        env, handle.get(), functionName, argumentsJson, nullptr, 0, false,
        outputFd, static_cast<size_t>(maxOutputBytes), true);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_cancelBookRuntime(
    JNIEnv* env,
    jclass,
    jlong nativeHandle
) {
    Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_cancel(env, nullptr, nativeHandle);
}

extern "C" JNIEXPORT void JNICALL
Java_org_tsuyomi_android_publication_MobiQuickJsNative_closeBookRuntime(
    JNIEnv* env,
    jclass,
    jlong nativeHandle
) {
    Java_org_tsuyomi_source_quickjsruntime_QuickJsNative_close(env, nullptr, nativeHandle);
}
