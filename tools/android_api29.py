#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0
"""Run planner-selected Android API 29 instrumentation on an owned emulator.

The runner deliberately uses the Gradle Wrapper directly for instrumentation:
ANDROID_SERIAL must be replaced with this run's verified serial and each device
invocation is serialized.  The local resource batch files are used only for the
optional compile/build phase, where they own the requested local resource mode.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as element_tree
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Iterable, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from android_ci_plan import AndroidCiPlan, git_paths, plan_for_paths


TASK_PATTERN = re.compile(r"^:[A-Za-z0-9_.:-]+$")
TEST_CLASS_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_$.]*(?:#[A-Za-z_][A-Za-z0-9_$]*)?$")


class RunnerError(RuntimeError):
    """A failure that should be recorded as a usable runner result."""


@dataclass(frozen=True)
class Profile:
    system_image: str
    avd_device: str
    renderer: str
    boot_timeout_seconds: int
    shutdown_timeout_seconds: int
    width: int
    height: int
    density: int
    font_scale: str
    rotation: int
    animations: str


@dataclass(frozen=True)
class SdkTools:
    root: Path
    adb: Path
    emulator: Path
    avdmanager: Path
    sdkmanager: Path


@dataclass(frozen=True)
class CommandResult:
    command: tuple[str, ...]
    returncode: int
    output: str


def utc_now() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the owned API 29 emulator against planner-selected Android instrumentation.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=SCRIPT_DIR.parent,
        help="monorepo root; defaults to the parent of this tools directory",
    )
    parser.add_argument(
        "--sdk",
        type=Path,
        help="Android SDK root; defaults to ANDROID_SDK_ROOT then ANDROID_HOME",
    )
    parser.add_argument("--base", default="", help="Git base SHA; missing or unavailable bases use the full plan")
    parser.add_argument("--head", default="HEAD", help="Git head revision passed to the planner")
    parser.add_argument(
        "--mode",
        choices=("high", "low", "ci"),
        default="low",
        help="high is required for local runs; ci is restricted to GitHub Actions",
    )
    parser.add_argument(
        "--test-class",
        help="focused diagnostic instrumentation class, optionally with #method",
    )
    parser.add_argument(
        "--task",
        action="append",
        default=[],
        metavar="TASK",
        help="focused diagnostic :module:connectedDebugAndroidTest task; repeatable",
    )
    parser.add_argument(
        "--build",
        action="store_true",
        help="run planner-selected build checks and Android-test assembly before instrumentation",
    )
    parser.add_argument(
        "--prepare-only",
        action="store_true",
        help="create, boot, configure, record, and remove the owned emulator without Gradle",
    )
    args = parser.parse_args(argv)

    if args.mode == "low":
        parser.error("--mode low is not available locally; choose explicit --mode high for local work")
    if args.mode == "ci" and os.environ.get("GITHUB_ACTIONS") != "true":
        parser.error("--mode ci is reserved for GitHub Actions (GITHUB_ACTIONS=true)")
    if args.test_class and not TEST_CLASS_PATTERN.fullmatch(args.test_class):
        parser.error("--test-class must be a Java/Kotlin qualified class, optionally followed by #method")
    for task in args.task:
        if not TASK_PATTERN.fullmatch(task) or not task.endswith(":connectedDebugAndroidTest"):
            parser.error("--task must be a :module:connectedDebugAndroidTest Gradle task")
    if args.prepare_only and (args.build or args.task or args.test_class):
        parser.error("--prepare-only cannot be combined with --build, --task, or --test-class")
    return args


def load_profile(path: Path) -> Profile:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except OSError as error:
        raise RunnerError(f"Cannot read API 29 profile {path}: {error}") from error
    except json.JSONDecodeError as error:
        raise RunnerError(f"Invalid API 29 profile {path}: {error}") from error

    try:
        display = raw["display"]
        profile = Profile(
            system_image=str(raw["system_image"]),
            avd_device=str(raw["avd_device"]),
            renderer=str(raw["renderer"]),
            boot_timeout_seconds=int(raw["boot_timeout_seconds"]),
            shutdown_timeout_seconds=int(raw["shutdown_timeout_seconds"]),
            width=int(display["width"]),
            height=int(display["height"]),
            density=int(display["density"]),
            font_scale=str(display["font_scale"]),
            rotation=int(display["rotation"]),
            animations=str(display["animations"]),
        )
    except (KeyError, TypeError, ValueError) as error:
        raise RunnerError(f"Profile {path} has a missing or invalid required setting: {error}") from error

    if not profile.system_image.startswith("system-images;"):
        raise RunnerError("Profile system_image must be an SDK system-images package ID")
    if profile.width <= 0 or profile.height <= 0 or profile.density <= 0:
        raise RunnerError("Profile display width, height, and density must be positive")
    if profile.boot_timeout_seconds <= 0 or profile.shutdown_timeout_seconds <= 0:
        raise RunnerError("Profile boot and shutdown timeouts must be positive")
    return profile


def resolve_sdk(requested: Path | None) -> Path:
    if requested is not None:
        candidate = requested
    else:
        configured = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
        if not configured:
            raise RunnerError("Android SDK is not set. Pass --sdk PATH or set ANDROID_SDK_ROOT (or ANDROID_HOME).")
        candidate = Path(configured)
    sdk = candidate.expanduser().resolve()
    if not sdk.is_dir():
        raise RunnerError(f"Android SDK root does not exist: {sdk}")
    return sdk


def executable(path: Path, batch: bool = False) -> Path | None:
    candidates = (path, path.with_suffix(".bat")) if batch and os.name == "nt" else (path, path.with_suffix(".exe"))
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return None


def newest_command_line_tool(sdk: Path, name: str) -> Path | None:
    root = sdk / "cmdline-tools"
    if not root.is_dir():
        return None
    preferred = executable(root / "latest" / "bin" / name, batch=True)
    if preferred:
        return preferred
    for directory in sorted((entry for entry in root.iterdir() if entry.is_dir()), reverse=True):
        candidate = executable(directory / "bin" / name, batch=True)
        if candidate:
            return candidate
    return None


def require_sdk_tools(sdk: Path, system_image: str) -> SdkTools:
    adb = executable(sdk / "platform-tools" / "adb")
    emulator = executable(sdk / "emulator" / "emulator")
    avdmanager = newest_command_line_tool(sdk, "avdmanager")
    sdkmanager = newest_command_line_tool(sdk, "sdkmanager")
    missing = [
        name
        for name, value in (
            ("platform-tools/adb", adb),
            ("emulator/emulator", emulator),
            ("cmdline-tools/*/bin/avdmanager", avdmanager),
            ("cmdline-tools/*/bin/sdkmanager", sdkmanager),
        )
        if value is None
    ]
    if missing:
        raise RunnerError(
            "Android SDK is missing required tools: "
            + ", ".join(missing)
            + ". Install Android command-line tools, platform-tools, and emulator; this runner never installs SDK packages."
        )

    image_directory = sdk.joinpath(*system_image.split(";"))
    if not (image_directory / "package.xml").is_file():
        raise RunnerError(
            f"Required API 29 system image is not installed: {system_image}. "
            f"Install it manually with {sdkmanager} --install {system_image!r}, then accept licenses outside this runner."
        )
    return SdkTools(sdk, adb, emulator, avdmanager, sdkmanager)


def package_revision(directory: Path) -> str | None:
    package_xml = directory / "package.xml"
    if not package_xml.is_file():
        return None
    try:
        root = element_tree.parse(package_xml).getroot()
    except (OSError, element_tree.ParseError):
        return None
    revision = next((element for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "revision"), None)
    if revision is None:
        return None
    values = [
        (child.tag.rsplit("}", 1)[-1], (child.text or "").strip())
        for child in revision
        if (child.text or "").strip()
    ]
    if not values:
        return (revision.text or "").strip() or None
    parts = [value for name, value in values if name in {"major", "minor", "micro"}]
    preview = next((value for name, value in values if name == "preview"), "")
    rendered = ".".join(parts) if parts else ".".join(value for _, value in values)
    return f"{rendered}-preview{preview}" if preview and preview != "0" else rendered


def sdk_package_evidence(sdk: Path, profile: Profile) -> dict[str, dict[str, str | None]]:
    packages = {
        "system_image": (profile.system_image, sdk.joinpath(*profile.system_image.split(";"))),
        "emulator": ("emulator", sdk / "emulator"),
        "platform_tools": ("platform-tools", sdk / "platform-tools"),
    }
    return {
        name: {"id": package_id, "revision": package_revision(directory)}
        for name, (package_id, directory) in packages.items()
    }


def append_log(path: Path, text: str) -> None:
    with path.open("a", encoding="utf-8", newline="") as handle:
        handle.write(text)
        if not text.endswith("\n"):
            handle.write("\n")


def display_command(command: Iterable[str]) -> str:
    values = list(command)
    return subprocess.list2cmdline(values) if os.name == "nt" else " ".join(repr(value) if any(char.isspace() for char in value) else value for value in values)


def capture_command(
    command: Sequence[str],
    *,
    log: Path,
    cwd: Path | None,
    env: dict[str, str] | None,
    timeout: int,
    input_text: str | None = None,
) -> CommandResult:
    append_log(log, f"$ {display_command(command)}")
    try:
        completed = subprocess.run(
            list(command),
            cwd=cwd,
            env=env,
            input=input_text,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired as error:
        output = error.stdout or ""
        if isinstance(output, bytes):
            output = output.decode(errors="replace")
        append_log(log, output)
        append_log(log, f"Command timed out after {timeout}s")
        raise RunnerError(f"Timed out after {timeout}s: {display_command(command)}") from error
    except OSError as error:
        raise RunnerError(f"Could not start {command[0]}: {error}") from error

    append_log(log, completed.stdout)
    append_log(log, f"[exit {completed.returncode}]")
    return CommandResult(tuple(command), completed.returncode, completed.stdout)


def run_gradle(command: Sequence[str], *, cwd: Path, env: dict[str, str], log: Path) -> int:
    append_log(log, f"$ {display_command(command)}")
    try:
        process = subprocess.Popen(
            list(command),
            cwd=cwd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
    except OSError as error:
        raise RunnerError(f"Could not start Gradle: {error}") from error

    assert process.stdout is not None
    with log.open("a", encoding="utf-8", newline="") as handle:
        for line in process.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
            handle.write(line)
    returncode = process.wait()
    append_log(log, f"[exit {returncode}]")
    return returncode


def clean_gradle_environment(sdk: Path, serial: str | None = None) -> dict[str, str]:
    env = os.environ.copy()
    env.pop("ANDROID_SERIAL", None)
    for key in tuple(env):
        if key.startswith("TSUYOMI_"):
            env.pop(key, None)
    env["ANDROID_SDK_ROOT"] = str(sdk)
    env["ANDROID_HOME"] = str(sdk)
    if serial is not None:
        env["ANDROID_SERIAL"] = serial
    return env


def batch_command(batch_file: Path, arguments: Sequence[str]) -> list[str]:
    command_processor = os.environ.get("COMSPEC", "cmd.exe")
    # arguments are planner-owned Gradle tasks/options, never unconstrained user text.
    return [command_processor, "/d", "/s", "/c", subprocess.list2cmdline([str(batch_file), *arguments])]


def gradle_wrapper(android_root: Path) -> Path:
    wrapper = android_root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise RunnerError(f"Gradle Wrapper is missing: {wrapper}")
    return wrapper


def build_command(android_root: Path, mode: str, tasks: Sequence[str]) -> list[str]:
    options = ["--console=plain", "--stacktrace", "--dependency-verification=strict"]
    if mode == "ci":
        return [str(gradle_wrapper(android_root)), "--no-daemon", *options, *tasks]
    if os.name == "nt":
        runner = android_root / "tools" / ("Run-Gradle-High.bat" if mode == "high" else "Run-Gradle-Low.bat")
        if not runner.is_file():
            raise RunnerError(f"Gradle resource runner is missing: {runner}")
        return batch_command(runner, [*options, *tasks])
    if mode == "high":
        return [str(gradle_wrapper(android_root)), f"--max-workers={os.cpu_count() or 1}", "--parallel", *options, *tasks]
    return [str(gradle_wrapper(android_root)), "--max-workers=2", "--no-parallel", "--no-daemon", *options, *tasks]


def instrumentation_command(android_root: Path, tasks: Sequence[str], test_class: str | None, mode: str) -> list[str]:
    command = [str(gradle_wrapper(android_root))]
    if mode != "high":
        command.append("--no-daemon")
    command.extend(
        (
            "--console=plain",
            "--stacktrace",
            "--dependency-verification=strict",
            "--max-workers=1",
            "--no-parallel",
        )
    )
    if test_class:
        command.append(f"-Pandroid.testInstrumentationRunnerArguments.class={test_class}")
    command.extend(tasks)
    if os.name == "nt":
        return batch_command(Path(command[0]), command[1:])
    return command


def unique_port() -> int:
    candidates = list(range(5554, 5682, 2))
    start = secrets.randbelow(len(candidates))
    for index in range(len(candidates)):
        port = candidates[(start + index) % len(candidates)]
        sockets: list[socket.socket] = []
        try:
            for candidate in (port, port + 1):
                listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                listener.bind(("127.0.0.1", candidate))
                sockets.append(listener)
        except OSError:
            continue
        finally:
            for listener in sockets:
                listener.close()
        return port
    raise RunnerError("No paired even emulator port is available in 5554-5682")


def temp_emulator_environment(sdk: Path, home: Path) -> dict[str, str]:
    env = os.environ.copy()
    env.pop("ANDROID_SERIAL", None)
    for key in tuple(env):
        if key.startswith("TSUYOMI_"):
            env.pop(key, None)
    env["ANDROID_SDK_ROOT"] = str(sdk)
    env["ANDROID_HOME"] = str(sdk)
    env["ANDROID_AVD_HOME"] = str(home / "avd")
    env["ANDROID_EMULATOR_HOME"] = str(home / "emulator")
    return env


def create_avd(tools: SdkTools, profile: Profile, name: str, env: dict[str, str], log: Path) -> None:
    Path(env["ANDROID_AVD_HOME"]).mkdir(parents=True, exist_ok=True)
    Path(env["ANDROID_EMULATOR_HOME"]).mkdir(parents=True, exist_ok=True)
    result = capture_command(
        [
            str(tools.avdmanager),
            "create",
            "avd",
            "--force",
            "--name",
            name,
            "--package",
            profile.system_image,
            "--device",
            profile.avd_device,
        ],
        log=log,
        cwd=None,
        env=env,
        timeout=90,
        input_text="no\n",
    )
    if result.returncode != 0:
        raise RunnerError(f"avdmanager could not create owned AVD {name}; see {log}")


def require_renderer(tools: SdkTools, profile: Profile, env: dict[str, str], log: Path) -> None:
    result = capture_command(
        [str(tools.emulator), "-help-gpu"],
        log=log,
        cwd=None,
        env=env,
        timeout=30,
    )
    if result.returncode != 0 or profile.renderer not in result.output:
        raise RunnerError(
            f"Installed emulator does not advertise required renderer {profile.renderer!r}. "
            "Install a compatible emulator package or change the shared profile deliberately; no renderer fallback is allowed."
        )


def start_emulator(
    tools: SdkTools,
    profile: Profile,
    name: str,
    port: int,
    env: dict[str, str],
    log: Path,
) -> subprocess.Popen[str]:
    command = [
        str(tools.emulator),
        "-avd",
        name,
        "-port",
        str(port),
        "-no-window",
        "-no-audio",
        "-no-boot-anim",
        "-no-snapshot",
        "-no-snapshot-save",
        "-wipe-data",
        "-gpu",
        profile.renderer,
        "-skin",
        f"{profile.width}x{profile.height}",
        "-dpi-device",
        str(profile.density),
    ]
    append_log(log, f"$ {display_command(command)}")
    try:
        with log.open("a", encoding="utf-8", newline="") as handle:
            return subprocess.Popen(command, env=env, stdout=handle, stderr=subprocess.STDOUT, text=True)
    except OSError as error:
        raise RunnerError(f"Could not start owned emulator: {error}") from error


def adb(
    tools: SdkTools,
    serial: str,
    arguments: Sequence[str],
    *,
    env: dict[str, str],
    log: Path,
    timeout: int = 10,
) -> CommandResult:
    return capture_command(
        [str(tools.adb), "-s", serial, *arguments],
        log=log,
        cwd=None,
        env=env,
        timeout=timeout,
    )


def owned_avd_name(tools: SdkTools, serial: str, env: dict[str, str], log: Path) -> str | None:
    result = adb(tools, serial, ("emu", "avd", "name"), env=env, log=log)
    if result.returncode != 0:
        return None
    lines = [line.strip() for line in result.output.splitlines() if line.strip()]
    if lines[-1:] == ["OK"]:
        lines.pop()
    return lines[0] if len(lines) == 1 else None


def require_owned_avd(tools: SdkTools, serial: str, expected_name: str, env: dict[str, str], log: Path) -> None:
    actual_name = owned_avd_name(tools, serial, env, log)
    if actual_name != expected_name:
        raise RunnerError(
            f"Refusing device operation: serial {serial} reported AVD {actual_name!r}, expected owned AVD {expected_name!r}."
        )


def wait_for_boot(
    tools: SdkTools,
    process: subprocess.Popen[str],
    serial: str,
    expected_name: str,
    profile: Profile,
    env: dict[str, str],
    log: Path,
) -> None:
    deadline = time.monotonic() + profile.boot_timeout_seconds
    while time.monotonic() < deadline:
        exited = process.poll()
        if exited is not None:
            raise RunnerError(f"Owned emulator process exited before boot with status {exited}; see {log}")
        try:
            state = adb(tools, serial, ("get-state",), env=env, log=log, timeout=5)
            if state.returncode == 0 and state.output.strip() == "device":
                boot = adb(tools, serial, ("shell", "getprop", "sys.boot_completed"), env=env, log=log, timeout=5)
                if boot.returncode == 0 and boot.output.strip() == "1":
                    require_owned_avd(tools, serial, expected_name, env, log)
                    return
        except RunnerError as error:
            append_log(log, f"Waiting for owned emulator boot: {error}")
        time.sleep(2)
    raise RunnerError(f"Owned emulator did not boot within {profile.boot_timeout_seconds}s; see {log}")


def configure_emulator(
    tools: SdkTools,
    serial: str,
    expected_name: str,
    profile: Profile,
    env: dict[str, str],
    log: Path,
) -> None:
    require_owned_avd(tools, serial, expected_name, env, log)
    display_commands = (
        (("shell", "wm", "size", f"{profile.width}x{profile.height}"), "display size"),
        (("shell", "wm", "density", str(profile.density)), "display density"),
    )
    for command, label in display_commands:
        result = adb(tools, serial, command, env=env, log=log)
        if result.returncode != 0:
            raise RunnerError(f"Could not configure owned emulator {label}; see {log}")
    settings = (
        ("system", "font_scale", profile.font_scale),
        ("system", "accelerometer_rotation", "0"),
        ("system", "user_rotation", str(profile.rotation)),
        ("global", "window_animation_scale", profile.animations),
        ("global", "transition_animation_scale", profile.animations),
        ("global", "animator_duration_scale", profile.animations),
    )
    for namespace, key, value in settings:
        result = adb(
            tools,
            serial,
            ("shell", "settings", "put", namespace, key, value),
            env=env,
            log=log,
        )
        if result.returncode != 0:
            raise RunnerError(f"Could not configure owned emulator setting {namespace}/{key}; see {log}")


def device_evidence(
    tools: SdkTools,
    serial: str,
    expected_name: str,
    env: dict[str, str],
    adb_log: Path,
    settings_log: Path,
    webview_log: Path,
    fingerprint_log: Path,
) -> dict[str, Any]:
    require_owned_avd(tools, serial, expected_name, env, adb_log)
    probes = {
        "avd_name": ("emu", "avd", "name"),
        "wm_size": ("shell", "wm", "size"),
        "wm_density": ("shell", "wm", "density"),
        "font_scale": ("shell", "settings", "get", "system", "font_scale"),
        "user_rotation": ("shell", "settings", "get", "system", "user_rotation"),
        "window_animation_scale": ("shell", "settings", "get", "global", "window_animation_scale"),
        "transition_animation_scale": ("shell", "settings", "get", "global", "transition_animation_scale"),
        "animator_duration_scale": ("shell", "settings", "get", "global", "animator_duration_scale"),
    }
    resolved: dict[str, str] = {}
    rendered: list[str] = []
    for label, command in probes.items():
        result = adb(tools, serial, command, env=env, log=adb_log)
        if result.returncode != 0:
            raise RunnerError(f"Could not inspect owned emulator setting {label}; see {adb_log}")
        value = result.output.strip()
        resolved[label] = value
        rendered.extend((f"[{label}]", value, ""))
    settings_log.write_text("\n".join(rendered), encoding="utf-8")

    webview = adb(tools, serial, ("shell", "dumpsys", "webviewupdate"), env=env, log=adb_log, timeout=20)
    system_fingerprint = adb(
        tools,
        serial,
        ("shell", "getprop", "ro.build.fingerprint"),
        env=env,
        log=adb_log,
        timeout=20,
    )
    properties = adb(tools, serial, ("shell", "getprop"), env=env, log=adb_log, timeout=20)
    webview_log.write_text(webview.output, encoding="utf-8")
    fingerprint_log.write_text(properties.output, encoding="utf-8")
    if webview.returncode != 0 or system_fingerprint.returncode != 0 or properties.returncode != 0:
        raise RunnerError(f"Could not capture WebView or system fingerprint evidence; see {adb_log}")
    return {
        "settings": resolved,
        "webview_dumpsys": webview.output,
        "system_fingerprint": system_fingerprint.output.strip(),
    }


def stop_emulator(
    tools: SdkTools,
    process: subprocess.Popen[str] | None,
    serial: str | None,
    expected_name: str | None,
    env: dict[str, str] | None,
    adb_log: Path | None,
    timeout_seconds: int,
) -> str | None:
    if process is None or process.poll() is not None:
        return None

    verified = False
    if serial and expected_name and env and adb_log:
        try:
            actual_name = owned_avd_name(tools, serial, env, adb_log)
        except RunnerError as error:
            append_log(adb_log, f"Could not verify owned AVD during cleanup: {error}")
        else:
            verified = actual_name == expected_name
            if verified:
                try:
                    adb(tools, serial, ("emu", "kill"), env=env, log=adb_log, timeout=10)
                except RunnerError as error:
                    append_log(adb_log, f"Owned AVD shutdown command failed: {error}")
                else:
                    try:
                        process.wait(timeout=timeout_seconds)
                        return None
                    except subprocess.TimeoutExpired:
                        append_log(adb_log, "Owned AVD did not stop after adb emu kill; terminating its owned process handle.")
            else:
                append_log(adb_log, "AVD identity guard failed; refusing adb shutdown and terminating only this runner's Popen handle.")

    # This object is the process handle created by start_emulator, never a discovered process.
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            return "Owned emulator process did not exit after terminate/kill"
    return None if verified else "Owned emulator required direct process termination after identity verification was unavailable"


def unique_tasks(tasks: Iterable[str]) -> tuple[str, ...]:
    return tuple(dict.fromkeys(tasks))

def instrumentation_precompile_tasks(tasks: Iterable[str]) -> tuple[str, ...]:
    precompile: list[str] = []
    for task in tasks:
        module = task.removesuffix(":connectedDebugAndroidTest")
        precompile.extend((f"{module}:assembleDebug", f"{module}:assembleDebugAndroidTest"))
    return unique_tasks(precompile)


def git_capture(repo_root: Path, arguments: Sequence[str]) -> bytes:
    command = ["git", "-C", str(repo_root), *arguments]
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise RunnerError(f"Could not run Git for local worktree planning: {error}") from error
    if result.returncode != 0:
        detail = result.stderr.decode(errors="replace").strip()
        raise RunnerError(f"Git worktree planning failed ({display_command(command)}): {detail}")
    return result.stdout


def nul_paths(output: bytes) -> list[str]:
    return [path.replace("\\", "/") for path in output.decode(errors="replace").split("\0") if path]


def planner_evidence(repo_root: Path, base: str, head: str, mode: str) -> tuple[AndroidCiPlan, dict[str, Any]]:
    if mode != "ci" and head != "HEAD":
        raise RunnerError("Local worktree overlay requires --head HEAD; use --mode ci for an immutable non-HEAD revision.")

    try:
        resolved_head = git_capture(repo_root, ("rev-parse", "--verify", f"{head}^{{commit}}")).decode(errors="replace").strip()
    except RunnerError:
        if mode != "ci":
            raise
        resolved_head = None
    paths, fallback_full = git_paths(repo_root, base, head)
    tracked_dirty_paths: list[str] = []
    untracked_paths: list[str] = []
    if mode != "ci":
        tracked_dirty_paths = nul_paths(git_capture(repo_root, ("diff", "--name-only", "-z", "HEAD", "--")))
        untracked_paths = nul_paths(git_capture(repo_root, ("ls-files", "--others", "--exclude-standard", "-z")))
        paths = sorted(set(paths).union(tracked_dirty_paths, untracked_paths))

    plan = plan_for_paths(repo_root, paths, force_full=fallback_full)
    return plan, {
        "base": base or None,
        "head": head,
        "resolved_head": resolved_head,
        "fallback_full": fallback_full,
        "paths": paths,
        "worktree_overlay": {
            "enabled": mode != "ci",
            "tracked_dirty_paths": tracked_dirty_paths,
            "untracked_paths": untracked_paths,
            "local_dirty_paths": sorted(set(tracked_dirty_paths).union(untracked_paths)),
        },
        "production_changed": plan.production_changed,
        "dependency_changed": plan.dependency_changed,
        "reasons": list(plan.reasons),
    }


def write_evidence(path: Path, evidence: dict[str, Any]) -> None:
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def host_evidence() -> dict[str, str]:
    return {
        "system": platform.system(),
        "release": platform.release(),
        "version": platform.version(),
        "machine": platform.machine(),
        "python": sys.version.split()[0],
    }


def version_evidence(tools: SdkTools, env: dict[str, str], log: Path) -> dict[str, str]:
    versions: dict[str, str] = {}
    for label, command in (
        ("java", ("java", "-version")),
        ("emulator", (str(tools.emulator), "-version")),
    ):
        try:
            result = capture_command(command, log=log, cwd=None, env=env, timeout=30)
        except RunnerError as error:
            versions[label] = f"unavailable: {error}"
        else:
            versions[label] = result.output.strip()
    return versions


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    repo_root = args.repo_root.expanduser().resolve()
    android_root = repo_root / "tsuyomi-android"
    if not repo_root.is_dir() or not android_root.is_dir():
        print(f"API 29 runner failed: --repo-root must contain tsuyomi-android: {repo_root}", file=sys.stderr)
        return 1
    profile_path = SCRIPT_DIR / "android_api29_profile.json"
    run_id = f"tsuyomi-ci-{datetime.now(UTC):%Y%m%dT%H%M%SZ}-{secrets.token_hex(4)}"
    run_directory = android_root / "build" / "api29-ci" / run_id
    run_directory.mkdir(parents=True, exist_ok=True)
    run_started = time.monotonic()

    focused_diagnostic = bool(args.task or args.test_class)
    full_preflight = bool(args.build and not args.prepare_only and not focused_diagnostic)
    scope = (
        "prepare_only"
        if args.prepare_only
        else "focused_diagnostic"
        if focused_diagnostic
        else "planner_selected_preflight"
        if full_preflight
        else "planner_selected_instrumentation"
    )
    evidence_path = run_directory / "environment.json"
    evidence: dict[str, Any] = {
        "schema_version": 1,
        "run_id": run_id,
        "started_at": utc_now(),
        "repo_root": str(repo_root),
        "android_root": str(android_root),
        "mode": args.mode,
        "scope": scope,
        "full_gate": full_preflight,
        "invocation": {
            "base": args.base or None,
            "head": args.head,
            "build": args.build,
            "prepare_only": args.prepare_only,
            "test_class": args.test_class,
            "task_override": list(args.task),
        },
        "host": host_evidence(),
        "logs": {
            "runner": "runner.log",
            "versions": "versions.log",
            "build": "build.log",
            "instrumentation": "instrumentation.log",
            "emulator": "emulator.log",
            "adb": "adb.log",
            "device_settings": "device-settings.txt",
            "webview": "webview.txt",
            "system_fingerprint": "system-fingerprint.txt",
        },
        "timings_seconds": {},
        "exit_status": None,
        "failure": None,
    }

    runner_log = run_directory / "runner.log"
    versions_log = run_directory / "versions.log"
    adb_log = run_directory / "adb.log"
    emulator_log = run_directory / "emulator.log"
    build_log = run_directory / "build.log"
    instrumentation_log = run_directory / "instrumentation.log"
    temp_home: Path | None = None
    emulator_process: subprocess.Popen[str] | None = None
    tools: SdkTools | None = None
    emulator_env: dict[str, str] | None = None
    serial: str | None = None
    avd_name: str | None = None
    profile: Profile | None = None
    exit_status = 1

    try:
        if not repo_root.is_dir() or not android_root.is_dir():
            raise RunnerError(f"--repo-root must contain tsuyomi-android: {repo_root}")
        profile = load_profile(profile_path)
        sdk = resolve_sdk(args.sdk)
        tools = require_sdk_tools(sdk, profile.system_image)
        plan, selected_plan = planner_evidence(repo_root, args.base, args.head, args.mode)
        selected_tasks = unique_tasks(args.task or plan.instrumentation_tasks)
        precompile_tasks = instrumentation_precompile_tasks(selected_tasks)
        if args.build:
            precompile_tasks = unique_tasks((*precompile_tasks, *plan.build_tasks))
        evidence["planner"] = selected_plan
        evidence["tasks"] = {
            "planner_build": list(plan.build_tasks),
            "precompile": list(precompile_tasks),
            "instrumentation": list(selected_tasks),
        }
        evidence["profile"] = {
            "system_image": profile.system_image,
            "avd_device": profile.avd_device,
            "renderer": profile.renderer,
            "display": {
                "width": profile.width,
                "height": profile.height,
                "density": profile.density,
                "font_scale": profile.font_scale,
                "rotation": profile.rotation,
                "animations": profile.animations,
            },
            "boot_timeout_seconds": profile.boot_timeout_seconds,
            "shutdown_timeout_seconds": profile.shutdown_timeout_seconds,
        }
        evidence["sdk"] = {
            "root": str(sdk),
            "packages": sdk_package_evidence(sdk, profile),
            "tools": {
                "adb": str(tools.adb),
                "emulator": str(tools.emulator),
                "avdmanager": str(tools.avdmanager),
                "sdkmanager": str(tools.sdkmanager),
            },
        }
        version_env = clean_gradle_environment(sdk)
        evidence["versions"] = version_evidence(tools, version_env, versions_log)
        write_evidence(evidence_path, evidence)

        if not args.prepare_only and precompile_tasks:
            command = build_command(android_root, args.mode, precompile_tasks)
            evidence.setdefault("commands", []).append({"phase": "precompile", "command": list(command)})
            write_evidence(evidence_path, evidence)
            phase_started = time.monotonic()
            result = run_gradle(command, cwd=android_root, env=clean_gradle_environment(sdk), log=build_log)
            evidence["timings_seconds"]["precompile"] = round(time.monotonic() - phase_started, 3)
            write_evidence(evidence_path, evidence)
            if result != 0:
                raise RunnerError(f"Pre-instrumentation compilation failed; see {build_log}")

        if not args.prepare_only and not selected_tasks:
            append_log(runner_log, "Planner selected no instrumentation tasks; no emulator was started.")
            exit_status = 0
            return exit_status

        phase_started = time.monotonic()
        temp_home = Path(tempfile.mkdtemp(prefix="tsuyomi-ci-avd-"))
        emulator_env = temp_emulator_environment(sdk, temp_home)
        avd_name = run_id
        port = unique_port()
        serial = f"emulator-{port}"
        evidence["avd"] = {
            "name": avd_name,
            "serial": serial,
            "port": port,
            "temporary_home": str(temp_home),
            "headless": True,
            "snapshots": "disabled",
            "fresh_application_state": True,
        }
        write_evidence(evidence_path, evidence)

        require_renderer(tools, profile, emulator_env, emulator_log)
        create_avd(tools, profile, avd_name, emulator_env, emulator_log)
        emulator_process = start_emulator(tools, profile, avd_name, port, emulator_env, emulator_log)
        wait_for_boot(tools, emulator_process, serial, avd_name, profile, emulator_env, adb_log)
        configure_emulator(tools, serial, avd_name, profile, emulator_env, adb_log)
        device = device_evidence(
            tools,
            serial,
            avd_name,
            emulator_env,
            adb_log,
            run_directory / "device-settings.txt",
            run_directory / "webview.txt",
            run_directory / "system-fingerprint.txt",
        )
        evidence["resolved_device_settings"] = device["settings"]
        evidence["webview_dumpsys"] = device["webview_dumpsys"]
        evidence["system_fingerprint"] = device["system_fingerprint"]
        write_evidence(evidence_path, evidence)
        evidence["timings_seconds"]["emulator_prepare"] = round(time.monotonic() - phase_started, 3)

        if not args.prepare_only:
            command = instrumentation_command(android_root, selected_tasks, args.test_class, args.mode)
            evidence.setdefault("commands", []).append({"phase": "instrumentation", "command": list(command)})
            write_evidence(evidence_path, evidence)
            phase_started = time.monotonic()
            result = run_gradle(
                command,
                cwd=android_root,
                env=clean_gradle_environment(sdk, serial=serial),
                log=instrumentation_log,
            )
            evidence["timings_seconds"]["instrumentation"] = round(time.monotonic() - phase_started, 3)
            write_evidence(evidence_path, evidence)
            if result != 0:
                raise RunnerError(f"Instrumentation phase failed; see {instrumentation_log}")

        exit_status = 0
    except KeyboardInterrupt:
        evidence["failure"] = "Interrupted"
        exit_status = 130
        print("API 29 runner interrupted; preserving evidence.", file=sys.stderr)
    except RunnerError as error:
        evidence["failure"] = str(error)
        print(f"API 29 runner failed: {error}", file=sys.stderr)
    except Exception as error:  # Preserve unexpected failures in evidence too.
        evidence["failure"] = f"Unexpected {type(error).__name__}: {error}"
        print(f"API 29 runner failed unexpectedly: {type(error).__name__}: {error}", file=sys.stderr)
    finally:
        if tools and emulator_process and emulator_process.poll() is None and serial and avd_name and emulator_env:
            try:
                require_owned_avd(tools, serial, avd_name, emulator_env, adb_log)
                captured = adb(tools, serial, ("logcat", "-d", "-t", "3000"), env=emulator_env, log=adb_log, timeout=20)
                (run_directory / "logcat.txt").write_text(captured.output, encoding="utf-8")
                evidence["logs"]["logcat"] = "logcat.txt"
            except (RunnerError, OSError) as error:
                evidence["logcat_warning"] = str(error)
        if tools and profile:
            cleanup_error = stop_emulator(
                tools,
                emulator_process,
                serial,
                avd_name,
                emulator_env,
                adb_log,
                profile.shutdown_timeout_seconds,
            )
            if cleanup_error:
                append_log(runner_log, cleanup_error)
                evidence["cleanup_warning"] = cleanup_error
                if exit_status == 0:
                    evidence["failure"] = cleanup_error
                    exit_status = 1
        if temp_home is not None:
            try:
                shutil.rmtree(temp_home)
                evidence["temporary_avd_home_removed"] = True
            except OSError as error:
                message = f"Could not remove owned temporary AVD home {temp_home}: {error}"
                append_log(runner_log, message)
                evidence["temporary_avd_home_removed"] = False
                evidence["cleanup_warning"] = message
                if exit_status == 0:
                    evidence["failure"] = message
                    exit_status = 1
        evidence["timings_seconds"]["total"] = round(time.monotonic() - run_started, 3)
        evidence["finished_at"] = utc_now()
        evidence["exit_status"] = exit_status
        write_evidence(evidence_path, evidence)
        print(f"API 29 evidence: {evidence_path} (exit {exit_status})")

    return exit_status


if __name__ == "__main__":
    raise SystemExit(main())
