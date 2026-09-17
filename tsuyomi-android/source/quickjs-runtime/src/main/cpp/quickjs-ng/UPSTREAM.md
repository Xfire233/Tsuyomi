<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: MIT -->

# Vendored QuickJS-ng source

- Upstream: <https://github.com/quickjs-ng/quickjs>
- Source tag: `v0.16.1` at commit `954dc53628e36891f93c359aa60895c2ae3dac6b`.
- Source archive: <https://codeload.github.com/quickjs-ng/quickjs/tar.gz/refs/tags/v0.16.1>.
- Source archive SHA-256: `4b3c11f37dab2c58bdeccbaeb23b923fa4a9798a45e50be6af55f3e75b616ea0` (re-downloaded and verified on 2026-09-12).
- License: MIT; retain the upstream `LICENSE` and the file-level MIT notices.

This directory is the minimal runtime source subset needed by `tsuyomi_quickjs`. The source tag, archive and license above are the provenance baseline; this is not a broad QuickJS upgrade.

## R-01 carried vendoring repair

`unicode_script()` in the verified `v0.16.1` archive ends its allocation-failure cleanup with `goto fail`, which re-enters the same cleanup block forever; the `is_ext` path also repeatedly frees its extension ranges. The adopted source diff is deliberately one line: its `fail:` epilogue now returns `-1` after freeing the extension ranges. It preserves the upstream error convention and leaves the normal Script/Script_Extensions paths unchanged.

As of 2026-09-12, `v0.16.2` (`1ab8676f4b6d6d669baeb5f21790fb9734636a20`) and the upstream `master` tip examined at `5301314ca299bb5755586363e7769e68123a38a5` retain that loop. There is therefore no upstream fix commit to adopt truthfully. This tracked, documented one-line carry is the narrow exception to the no-hand-edit rule; it must be dropped in favor of a released upstream fix once one exists. It is not an upstream security advisory or CVE claim.

The ignored before evidence is `.local/ai-reviews/r01-upstream/libunicode.unicode_script.before.c`; the verified original tag archive is stored beside it. The bounded host-native regression is `test/cpp/quickjs_unicode_script_failure_test.c`, enabled only with `-DTSUYOMI_QUICKJS_BUILD_NATIVE_TESTS=ON`. It launches separate Script and Script_Extensions allocation-failure children, each capped at 5 seconds; every injected failure must return `-1` and release all tracked allocations.

For the preserved pre-remediation source, compile the tracked harness against the archived tag in an isolated ignored directory. Its parent must end nonzero after its bounded child timeout; do not replace the working vendor file to obtain that result:

```sh
mkdir -p .local/r01-before
tar -xzf .local/ai-reviews/r01-upstream/quickjs-ng-v0.16.1.tar.gz -C .local/r01-before
cc -std=c11 -D_POSIX_C_SOURCE=200809L -I.local/r01-before/quickjs-0.16.1 source/quickjs-runtime/src/test/cpp/quickjs_unicode_script_failure_test.c .local/r01-before/quickjs-0.16.1/libunicode.c -o .local/r01-before/unicode-script-failure
.local/r01-before/unicode-script-failure
```

To exercise the repaired tree on a POSIX development host:

```sh
cmake -S source/quickjs-runtime/src/main/cpp -B .local/r01-native -DTSUYOMI_QUICKJS_BUILD_NATIVE_TESTS=ON
cmake --build .local/r01-native --target tsuyomi_quickjs_unicode_script_failure_test
ctest --test-dir .local/r01-native --output-on-failure --tests-regex '^QuickJsUnicodeScriptAllocationFailure$'
```

## Bounded JNI reachability proof

The bridge exposes only source evaluation/call (`create`, `prepareOperation`, `evaluateModule`, `callJson`, `cancel`, `close`). It does not call or expose `JS_ReadObject`, `JS_EvalBinary`, or `JS_SetSharedArrayBufferFunctions`. This does not mean JavaScript intrinsics are absent: `JS_NewContext` installs typed-array intrinsics. Blocking `Atomics.wait` remains denied by the runtime's default `can_block=false`; the bridge never calls `JS_SetCanBlock`. The JNI regression checks that admission with a zero-duration request, without enabling blocking or introducing an unsafe host API. Execute the real JNI Unicode, intrinsic-admission and close/submit paths:

```sh
grep -nE 'Java_org_tsuyomi_source_quickjsruntime_QuickJsNative|JS_(ReadObject|EvalBinary|SetSharedArrayBufferFunctions)' source/quickjs-runtime/src/main/cpp/tsuyomi_quickjs_jni.cpp
grep -nE 'external fun (create|prepareOperation|evaluateModule|callJson|cancel|close)' source/quickjs-runtime/src/main/kotlin/org/tsuyomi/source/quickjsruntime/QuickJsRuntimeLane.kt
./gradlew :source:quickjs-runtime:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.tsuyomi.source.quickjsruntime.QuickJsRuntimeLaneInstrumentedTest
```

Do not infer reachability or a vulnerability from vendor-table/bytecode symbols compiled inside QuickJS; the commands above establish only the current public JNI surface and the safe source-evaluation consumer path.
