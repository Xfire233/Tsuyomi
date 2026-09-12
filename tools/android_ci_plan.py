#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import argparse
import json
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

FULL_BUILD_TASKS = (
    ":app:assembleDebug",
    ":app:lintDebug",
    ":core:ui:lintDebug",
    ":core:security:lintDebug",
    ":core:database:lintDebug",
    ":shared:model:test",
    ":shared:locator:test",
    ":shared:smart-shelf:test",
    ":shared:backup:test",
    ":shared:source-contract:test",
    ":reader:engine:test",
    ":app:testDebugUnitTest",
    ":core:display:testDebugUnitTest",
    ":core:files:testDebugUnitTest",
    ":core:media:testDebugUnitTest",
    ":core:security:testDebugUnitTest",
    ":core:network:testDebugUnitTest",
    ":core:preferences:testDebugUnitTest",
    ":feature:library:testDebugUnitTest",
    ":source:extension-manager:testDebugUnitTest",
    ":source:extension-testkit:testDebugUnitTest",
    ":core:ui:validateDebugScreenshotTest",
    ":feature:library:validateDebugScreenshotTest",
    ":feature:browse:validateDebugScreenshotTest",
    ":feature:settings:validateDebugScreenshotTest",
)
FULL_INSTRUMENTATION_TASKS = (
    ":app:connectedDebugAndroidTest",
    ":feature:backup:connectedDebugAndroidTest",
    ":feature:book:connectedDebugAndroidTest",
    ":feature:browse:connectedDebugAndroidTest",
    ":feature:library:connectedDebugAndroidTest",
    ":feature:settings:connectedDebugAndroidTest",
    ":reader:ui:connectedDebugAndroidTest",
    ":core:media:connectedDebugAndroidTest",
    ":core:ui:connectedDebugAndroidTest",
    ":core:security:connectedDebugAndroidTest",
    ":core:database:connectedDebugAndroidTest",
    ":core:webview:connectedDebugAndroidTest",
    ":source:quickjs-runtime:connectedDebugAndroidTest",
)
JVM_TASKS = {
    ":app": ":app:testDebugUnitTest",
    ":shared:model": ":shared:model:test",
    ":shared:locator": ":shared:locator:test",
    ":shared:smart-shelf": ":shared:smart-shelf:test",
    ":shared:backup": ":shared:backup:test",
    ":shared:source-contract": ":shared:source-contract:test",
    ":reader:engine": ":reader:engine:test",
    ":core:display": ":core:display:testDebugUnitTest",
    ":core:files": ":core:files:testDebugUnitTest",
    ":core:media": ":core:media:testDebugUnitTest",
    ":core:security": ":core:security:testDebugUnitTest",
    ":core:preferences": ":core:preferences:testDebugUnitTest",
    ":core:network": ":core:network:testDebugUnitTest",
    ":feature:library": ":feature:library:testDebugUnitTest",
    ":source:extension-manager": ":source:extension-manager:testDebugUnitTest",
    ":source:extension-testkit": ":source:extension-testkit:testDebugUnitTest",
}
SCREENSHOT_TASKS = {
    ":core:ui": ":core:ui:validateDebugScreenshotTest",
    ":feature:library": ":feature:library:validateDebugScreenshotTest",
    ":feature:browse": ":feature:browse:validateDebugScreenshotTest",
    ":feature:settings": ":feature:settings:validateDebugScreenshotTest",
}
INSTRUMENTED_MODULES = {
    ":app",
    ":feature:backup",
    ":feature:book",
    ":feature:browse",
    ":feature:library",
    ":feature:settings",
    ":reader:ui",
    ":core:media",
    ":core:ui",
    ":core:security",
    ":core:database",
    ":core:webview",
    ":source:quickjs-runtime",
}
GLOBAL_ANDROID_INPUTS = {
    ".github/workflows/android-quality.yml",
    "tools/android_ci_plan.py",
    "tools/android_api29.py",
    "tools/android_api29_profile.json",
    "tsuyomi-android/build.gradle.kts",
    "tsuyomi-android/settings.gradle.kts",
    "tsuyomi-android/gradle.properties",
    "tsuyomi-android/gradlew",
    "tsuyomi-android/gradlew.bat",
}
PROJECT_DEPENDENCY = re.compile(r'project\(\s*"(:[^"]+)"\s*\)')
DEPENDENCY_INPUT_SUFFIXES = (
    ".gradle.kts",
    "gradle.lockfile",
    "gradle/verification-metadata.xml",
    "gradle/libs.versions.toml",
)


@dataclass(frozen=True)
class AndroidCiPlan:
    production_changed: bool
    dependency_changed: bool
    build_tasks: tuple[str, ...]
    instrumentation_tasks: tuple[str, ...]
    reasons: tuple[str, ...]

    @property
    def changed(self) -> bool:
        return self.production_changed

    @property
    def instrumentation_compile_tasks(self) -> tuple[str, ...]:
        return tuple(task.replace(":connectedDebugAndroidTest", ":assembleDebugAndroidTest") for task in self.instrumentation_tasks)

    def as_outputs(self) -> dict[str, str]:
        return {
            "changed": str(self.changed).lower(),
            "production_changed": str(self.production_changed).lower(),
            "dependency_changed": str(self.dependency_changed).lower(),
            "device_needed": str(bool(self.instrumentation_tasks)).lower(),
            "build_tasks": " ".join(self.build_tasks),
            "instrumentation_compile_tasks": " ".join(self.instrumentation_compile_tasks),
            "instrumentation_tasks": " ".join(self.instrumentation_tasks),
        }


def module_for_path(path: str) -> str | None:
    prefix = "tsuyomi-android/"
    if not path.startswith(prefix):
        return None
    parts = path[len(prefix):].split("/")
    if parts[0] == "app":
        return ":app"
    if len(parts) >= 2 and parts[0] in {"core", "feature", "reader", "shared", "source"}:
        return f":{parts[0]}:{parts[1]}"
    return None


def source_kind(path: str) -> str:
    if "/src/androidTest/" in path:
        return "instrumentation"
    if "/src/screenshotTest/" in path or "/src/screenshotTestDebug/" in path:
        return "screenshot"
    if "/src/test/" in path:
        return "unit"
    if "/src/main/" in path:
        return "main"
    if path.endswith(DEPENDENCY_INPUT_SUFFIXES):
        return "dependency"
    return "module"


def is_dependency_input(path: str) -> bool:
    return path.endswith(DEPENDENCY_INPUT_SUFFIXES) or path.startswith("tsuyomi-android/gradle/")


def is_android_module(repo_root: Path, module: str) -> bool:
    relative = module.removeprefix(":").replace(":", "/")
    build_file = repo_root / "tsuyomi-android" / relative / "build.gradle.kts"
    if not build_file.is_file():
        return False
    text = build_file.read_text(encoding="utf-8", errors="ignore")
    return "android" in text or "tsuyomi.android" in text

def reverse_module_dependencies(repo_root: Path) -> dict[str, set[str]]:
    android_root = repo_root / "tsuyomi-android"
    reverse: dict[str, set[str]] = {}
    for build_file in android_root.rglob("build.gradle.kts"):
        relative = build_file.relative_to(android_root)
        if "build" in relative.parts or build_file == android_root / "build.gradle.kts":
            continue
        module_parts = relative.parent.parts
        if not module_parts:
            continue
        consumer = ":" + ":".join(module_parts)
        for dependency in PROJECT_DEPENDENCY.findall(build_file.read_text(encoding="utf-8", errors="ignore")):
            reverse.setdefault(dependency, set()).add(consumer)
    return reverse


def transitive_consumers(reverse: dict[str, set[str]], module: str) -> set[str]:
    affected = {module}
    pending = [module]
    while pending:
        dependency = pending.pop()
        for consumer in reverse.get(dependency, ()):
            if consumer not in affected:
                affected.add(consumer)
                pending.append(consumer)
    return affected


def plan_for_paths(repo_root: Path, paths: Iterable[str], force_full: bool = False) -> AndroidCiPlan:
    normalized = tuple(sorted({path.replace("\\", "/") for path in paths if path}))
    build_tasks: set[str] = set()
    instrumentation_tasks: set[str] = set()
    reasons: list[str] = []
    production_changed = False
    dependency_changed = False
    full = force_full
    reverse_dependencies = reverse_module_dependencies(repo_root)

    for path in normalized:
        if path in GLOBAL_ANDROID_INPUTS or path.startswith("tsuyomi-android/build-logic/"):
            full = True
            production_changed = True
            dependency_changed |= is_dependency_input(path)
            reasons.append(f"{path}: shared Android build or CI input")
            continue
        if not path.startswith("tsuyomi-android/") or path.endswith(".md"):
            continue

        module = module_for_path(path)
        if module is None:
            if path.startswith("tsuyomi-android/gradle/"):
                full = True
                production_changed = True
                dependency_changed = True
                reasons.append(f"{path}: shared Gradle input")
            continue

        production_changed = True
        kind = source_kind(path)
        dependency_changed |= is_dependency_input(path)
        reasons.append(f"{path}: {module} {kind}")

        if kind in {"main", "module", "dependency"}:
            build_tasks.add(":app:assembleDebug")
            if is_android_module(repo_root, module):
                build_tasks.add(f"{module}:lintDebug")
            affected_modules = transitive_consumers(reverse_dependencies, module)
            for affected_module in affected_modules:
                if affected_module in JVM_TASKS:
                    build_tasks.add(JVM_TASKS[affected_module])
                if affected_module in SCREENSHOT_TASKS:
                    build_tasks.add(SCREENSHOT_TASKS[affected_module])
                if affected_module in INSTRUMENTED_MODULES:
                    instrumentation_tasks.add(f"{affected_module}:connectedDebugAndroidTest")
        elif kind == "unit" and module in JVM_TASKS:
            build_tasks.add(JVM_TASKS[module])
        elif kind == "screenshot" and module in SCREENSHOT_TASKS:
            build_tasks.add(SCREENSHOT_TASKS[module])
        elif kind == "instrumentation":
            instrumentation_tasks.add(f"{module}:connectedDebugAndroidTest")

    if full:
        production_changed = True
        build_tasks = set(FULL_BUILD_TASKS)
        instrumentation_tasks = set(FULL_INSTRUMENTATION_TASKS)
        reasons.append("conservative full Android verification")

    return AndroidCiPlan(
        production_changed=production_changed,
        dependency_changed=dependency_changed,
        build_tasks=tuple(task for task in FULL_BUILD_TASKS if task in build_tasks)
        + tuple(sorted(build_tasks - set(FULL_BUILD_TASKS))),
        instrumentation_tasks=tuple(task for task in FULL_INSTRUMENTATION_TASKS if task in instrumentation_tasks)
        + tuple(sorted(instrumentation_tasks - set(FULL_INSTRUMENTATION_TASKS))),
        reasons=tuple(reasons),
    )


def git_paths(repo_root: Path, base: str, head: str) -> tuple[list[str], bool]:
    if not base or set(base) == {"0"}:
        return [], True
    exists = subprocess.run(
        ["git", "-C", str(repo_root), "cat-file", "-e", f"{base}^{{commit}}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if exists.returncode != 0:
        return [], True
    merge_base = subprocess.run(
        ["git", "-C", str(repo_root), "merge-base", base, head],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if merge_base.returncode != 0:
        return [], True
    comparison_base = merge_base.stdout.decode(errors="replace").strip()
    if not comparison_base:
        return [], True
    result = subprocess.run(
        ["git", "-C", str(repo_root), "diff", "--no-renames", "--name-only", comparison_base, head, "--"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        return [], True
    return [line for line in result.stdout.decode(errors="replace").splitlines() if line], False


def write_github_output(path: Path, outputs: dict[str, str]) -> None:
    with path.open("a", encoding="utf-8") as handle:
        for key, value in outputs.items():
            handle.write(f"{key}={value}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Plan affected Tsuyomi Android CI tasks")
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--base", default="")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--github-output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = args.repo_root.resolve()
    paths, force_full = git_paths(repo_root, args.base, args.head)
    plan = plan_for_paths(repo_root, paths, force_full=force_full)
    outputs = plan.as_outputs()
    if args.github_output is not None:
        write_github_output(args.github_output, outputs)
    print(json.dumps({**outputs, "reasons": plan.reasons}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
