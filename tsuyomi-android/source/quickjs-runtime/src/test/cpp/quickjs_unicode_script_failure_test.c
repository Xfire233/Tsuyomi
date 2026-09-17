/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#include <windows.h>
#else
#include <errno.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#endif

#include "libunicode.h"

enum {
    kMaximumFailingAllocations = 64,
    kChildTimeoutMilliseconds = 5000,
};

typedef struct {
    unsigned allocation_count;
    unsigned free_count;
    unsigned allocation_attempt;
    unsigned fail_at;
    int failure_injected;
    int unexpected_allocation_failure;
} AllocationProbe;

static void *failAtAllocation(void *opaque, void *ptr, size_t size)
{
    AllocationProbe *probe = opaque;
    void *result;

    if (size == 0) {
        if (ptr != NULL) {
            probe->free_count++;
            free(ptr);
        }
        return NULL;
    }

    probe->allocation_attempt++;
    if (probe->allocation_attempt == probe->fail_at) {
        probe->failure_injected = 1;
        return NULL;
    }

    result = realloc(ptr, size);
    if (result == NULL) {
        probe->unexpected_allocation_failure = 1;
        return NULL;
    }
    if (ptr == NULL)
        probe->allocation_count++;
    return result;
}

static int hasValidRange(const CharRange *range)
{
    int index;

    if (range->len <= 0 || (range->len & 1) != 0)
        return 0;
    for (index = 0; index < range->len; index += 2) {
        if (range->points[index] >= range->points[index + 1])
            return 0;
    }
    return 1;
}

static int runFailureSequence(const char *mode, int scriptExtensions)
{
    unsigned failAt;

    for (failAt = 1; failAt <= kMaximumFailingAllocations; failAt++) {
        AllocationProbe probe = { 0 };
        probe.fail_at = failAt;
        CharRange range;
        int result;
        int rangeWasValid;

        cr_init(&range, &probe, failAtAllocation);
        result = unicode_script(&range, "Latin", scriptExtensions != 0);
        rangeWasValid = hasValidRange(&range);
        cr_free(&range);

        if (probe.unexpected_allocation_failure) {
            fprintf(stderr, "%s: allocator failed before injection at allocation %u\n", mode, failAt);
            return 1;
        }
        if (probe.allocation_count != probe.free_count) {
            fprintf(
                stderr,
                "%s: allocation cleanup mismatch at allocation %u (%u allocated, %u freed)\n",
                mode,
                failAt,
                probe.allocation_count,
                probe.free_count
            );
            return 1;
        }
        if (probe.failure_injected) {
            if (result != -1) {
                fprintf(stderr, "%s: injected allocation failure %u returned %d, expected -1\n", mode, failAt, result);
                return 1;
            }
            continue;
        }
        if (result != 0 || !rangeWasValid) {
            fprintf(stderr, "%s: normal Unicode Script result is invalid (%d)\n", mode, result);
            return 1;
        }
        return 0;
    }

    fprintf(stderr, "%s: did not reach a successful allocation after %u attempts\n", mode, kMaximumFailingAllocations);
    return 1;
}

static int runChildMode(const char *mode)
{
    if (strcmp(mode, "--script") == 0)
        return runFailureSequence("Script", 0);
    if (strcmp(mode, "--script-extensions") == 0)
        return runFailureSequence("Script_Extensions", 1);
    fprintf(stderr, "unknown child mode: %s\n", mode);
    return 2;
}

#if defined(_WIN32)
static int runChildProcess(const char *self, const char *mode)
{
    const size_t commandLength = strlen(self) + strlen(mode) + 5;
    char *command = malloc(commandLength);
    STARTUPINFOA startupInfo;
    PROCESS_INFORMATION processInfo;
    DWORD exitCode = 1;
    DWORD waitResult;

    if (command == NULL) {
        fprintf(stderr, "could not allocate child command line\n");
        return 1;
    }
    snprintf(command, commandLength, "\"%s\" %s", self, mode);
    ZeroMemory(&startupInfo, sizeof(startupInfo));
    ZeroMemory(&processInfo, sizeof(processInfo));
    startupInfo.cb = sizeof(startupInfo);
    if (!CreateProcessA(NULL, command, NULL, NULL, FALSE, 0, NULL, NULL, &startupInfo, &processInfo)) {
        fprintf(stderr, "could not start %s child (%lu)\n", mode, GetLastError());
        free(command);
        return 1;
    }
    free(command);

    waitResult = WaitForSingleObject(processInfo.hProcess, kChildTimeoutMilliseconds);
    if (waitResult == WAIT_TIMEOUT) {
        fprintf(stderr, "%s child exceeded %dms timeout\n", mode, kChildTimeoutMilliseconds);
        TerminateProcess(processInfo.hProcess, 1);
        WaitForSingleObject(processInfo.hProcess, INFINITE);
    } else if (waitResult == WAIT_OBJECT_0) {
        GetExitCodeProcess(processInfo.hProcess, &exitCode);
    } else {
        fprintf(stderr, "could not wait for %s child (%lu)\n", mode, GetLastError());
    }
    CloseHandle(processInfo.hThread);
    CloseHandle(processInfo.hProcess);
    return waitResult == WAIT_OBJECT_0 && exitCode == 0 ? 0 : 1;
}
#else
static int runChildProcess(const char *self, const char *mode)
{
    const pid_t child = fork();
    int elapsedMilliseconds;

    if (child < 0) {
        perror("fork");
        return 1;
    }
    if (child == 0) {
        execl(self, self, mode, (char *)NULL);
        _exit(127);
    }

    for (elapsedMilliseconds = 0; elapsedMilliseconds < kChildTimeoutMilliseconds; elapsedMilliseconds += 50) {
        int status;
        const pid_t waited = waitpid(child, &status, WNOHANG);
        if (waited == child)
            return WIFEXITED(status) && WEXITSTATUS(status) == 0 ? 0 : 1;
        if (waited < 0 && errno != EINTR) {
            perror("waitpid");
            return 1;
        }
        if (waited == 0) {
            struct timespec pause;
            pause.tv_sec = 0;
            pause.tv_nsec = 50L * 1000L * 1000L;
            nanosleep(&pause, NULL);
        }
    }

    fprintf(stderr, "%s child exceeded %dms timeout\n", mode, kChildTimeoutMilliseconds);
    kill(child, SIGKILL);
    waitpid(child, NULL, 0);
    return 1;
}
#endif

int main(int argc, char **argv)
{
    if (argc == 2)
        return runChildMode(argv[1]);
    if (argc != 1) {
        fprintf(stderr, "usage: %s [--script|--script-extensions]\n", argv[0]);
        return 2;
    }
    if (runChildProcess(argv[0], "--script") != 0)
        return 1;
    return runChildProcess(argv[0], "--script-extensions");
}
