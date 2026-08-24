# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

"""Capture app-private Tsuyomi review submissions through ADB.

Logcat is only a wake-up channel. The review text is read from the debuggable app's
no-backup directory with ``adb exec-out run-as`` and verified before it is exposed to OMP.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

PACKAGE = "org.tsuyomi.prototype.uiatlas"
LOG_TAG = "TsuyomiReviewBridge"
LOG_MARKER = "REVIEW_READY "
SIGNAL_FILE = "no_backup/interactive-review-signal-v1.json"
PAYLOAD_FILE = "no_backup/interactive-review-live-v1.json"
SIGNAL_SCHEMA = "tsuyomi-live-review-signal-v1"
REVIEW_SCHEMA = "tsuyomi-interactive-prototype-review-v2"
EVENT_SCHEMA = "tsuyomi-live-review-event-v1"
STATE_SCHEMA = "tsuyomi-live-review-bridge-state-v1"
EVENT_PREFIX = "TSUYOMI_REVIEW_EVENT "
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
NODE_PATTERN = re.compile(r"^[LBMSX][0-9]{2}$")
SESSION_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
SIGNAL_KEYS = {
    "schema",
    "sessionId",
    "revision",
    "kind",
    "applicationId",
    "buildId",
    "nodeId",
    "route",
    "profile",
    "reviewSha256",
    "submittedAt",
}


class BridgeError(RuntimeError):
    pass

@dataclass(frozen=True)
class SubmissionPolicy:
    package: str
    active_profiles: frozenset[str]


@dataclass(frozen=True)
class PersistenceContext:
    device: str
    output_root: Path
    root: Path


def monorepo_root() -> Path:
    return Path(__file__).resolve().parents[4]


def resolve_adb(explicit: str | None) -> str:
    if explicit:
        path = Path(explicit).expanduser()
        if path.is_file():
            return str(path)
        resolved = shutil.which(explicit)
        if resolved:
            return resolved
        raise BridgeError(f"ADB executable not found: {explicit}")

    resolved = shutil.which("adb")
    if resolved:
        return resolved

    candidates: list[Path] = []
    for variable in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        value = os.environ.get(variable)
        if value:
            candidates.append(Path(value) / "platform-tools" / "adb.exe")
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        candidates.append(Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe")
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    raise BridgeError("ADB not found; pass --adb or configure ANDROID_SDK_ROOT")


def run_adb(adb: str, device: str, *args: str) -> bytes:
    completed = subprocess.run(
        [adb, "-s", device, *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if completed.returncode != 0:
        detail = completed.stderr.decode("utf-8", errors="replace").strip()
        raise BridgeError(f"adb {' '.join(args)} failed: {detail or completed.returncode}")
    return completed.stdout


def read_private_file(adb: str, device: str, package: str, relative_path: str) -> bytes:
    return run_adb(adb, device, "exec-out", "run-as", package, "cat", relative_path)


def parse_json_bytes(raw: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BridgeError(f"Invalid {label} JSON: {error}") from error
    if not isinstance(value, dict):
        raise BridgeError(f"{label} must be a JSON object")
    return value


def parse_timestamp(value: Any, label: str) -> str:
    if not isinstance(value, str):
        raise BridgeError(f"{label} must be an ISO-8601 string")
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise BridgeError(f"{label} is not a valid ISO-8601 timestamp") from error
    return value


def load_active_profiles(root: Path) -> set[str]:
    policy_path = root / "tools" / "skills" / "tsuyomi-android-review" / "review-policy.json"
    try:
        policy = json.loads(policy_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BridgeError(f"Cannot read review policy: {error}") from error
    profiles = policy.get("activeProfiles")
    if not isinstance(profiles, list) or not profiles or not all(isinstance(item, str) for item in profiles):
        raise BridgeError("review-policy.json activeProfiles is invalid")
    return set(profiles)


def validate_signal(signal: dict[str, Any], policy: SubmissionPolicy) -> None:
    missing = SIGNAL_KEYS - signal.keys()
    extra = signal.keys() - SIGNAL_KEYS
    if missing or extra:
        raise BridgeError(f"Signal keys mismatch; missing={sorted(missing)} extra={sorted(extra)}")
    if signal["schema"] != SIGNAL_SCHEMA:
        raise BridgeError(f"Unsupported signal schema: {signal['schema']}")
    if signal["applicationId"] != policy.package:
        raise BridgeError("Signal applicationId does not match target package")
    if not isinstance(signal["revision"], int) or isinstance(signal["revision"], bool) or signal["revision"] < 1:
        raise BridgeError("Signal revision must be a positive integer")
    if signal["kind"] not in {"node", "batch_ready"}:
        raise BridgeError(f"Unsupported submission kind: {signal['kind']}")
    if not isinstance(signal["sessionId"], str) or not SESSION_PATTERN.fullmatch(signal["sessionId"]):
        raise BridgeError("Signal sessionId is not a UUID")
    if not isinstance(signal["buildId"], str) or not SHA256_PATTERN.fullmatch(signal["buildId"]):
        raise BridgeError("Signal buildId is not SHA-256")
    if not isinstance(signal["reviewSha256"], str) or not SHA256_PATTERN.fullmatch(signal["reviewSha256"]):
        raise BridgeError("Signal reviewSha256 is not SHA-256")
    if not isinstance(signal["nodeId"], str) or not NODE_PATTERN.fullmatch(signal["nodeId"]):
        raise BridgeError("Signal nodeId is invalid")
    if not isinstance(signal["route"], str) or not signal["route"]:
        raise BridgeError("Signal route is invalid")
    if signal["profile"] not in policy.active_profiles:
        raise BridgeError(f"Submission profile {signal['profile']} is not active under review-policy.json")
    parse_timestamp(signal["submittedAt"], "submittedAt")


def validate_review_payload(
    signal: dict[str, Any],
    review: dict[str, Any],
    review_raw: bytes,
    policy: SubmissionPolicy,
) -> None:
    observed_hash = hashlib.sha256(review_raw).hexdigest()
    if observed_hash != signal["reviewSha256"]:
        raise BridgeError(
            f"Review payload hash mismatch: signal={signal['reviewSha256']} observed={observed_hash}"
        )
    if review.get("schema") != REVIEW_SCHEMA:
        raise BridgeError(f"Unsupported review schema: {review.get('schema')}")
    if review.get("provisional") is not True or review.get("productionAuthorized") is not False:
        raise BridgeError("Live review payload crossed the provisional/authorization boundary")
    build = review.get("build")
    if not isinstance(build, dict):
        raise BridgeError("Review build identity is missing")
    if build.get("applicationId") != policy.package or build.get("buildId") != signal["buildId"]:
        raise BridgeError("Review build identity does not match the signal")
    catalog = review.get("reviewCatalog")
    if not isinstance(catalog, list) or signal["nodeId"] not in {
        item.get("id") for item in catalog if isinstance(item, dict)
    }:
        raise BridgeError("Signal nodeId is absent from the exported Review Graph")


def validate_submission(
    signal: dict[str, Any],
    review: dict[str, Any],
    review_raw: bytes,
    policy: SubmissionPolicy,
) -> None:
    validate_signal(signal, policy)
    validate_review_payload(signal, review, review_raw, policy)


def atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_bytes(data)
    os.replace(temporary, path)


def load_json_file(path: Path, default: dict[str, Any]) -> dict[str, Any]:
    if not path.exists():
        return default
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BridgeError(f"Cannot read {path}: {error}") from error
    if not isinstance(value, dict):
        raise BridgeError(f"{path} must contain a JSON object")
    return value


def relative_display(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.resolve().as_posix()


def load_bridge_state(context: PersistenceContext) -> tuple[Path, dict[str, Any]]:
    state_path = context.output_root / "bridge-state.json"
    state = load_json_file(state_path, {"schema": STATE_SCHEMA, "devices": {}})
    if state.get("schema") != STATE_SCHEMA or not isinstance(state.get("devices"), dict):
        raise BridgeError("bridge-state.json has an unsupported schema")
    return state_path, state


def should_skip_revision(state: dict[str, Any], key: str, revision: int, digest: str) -> bool:
    previous = state["devices"].get(key)
    if not isinstance(previous, dict):
        return False
    previous_revision = previous.get("revision")
    if previous_revision == revision:
        if previous.get("reviewSha256") != digest:
            raise BridgeError("The same live-review revision was observed with different content")
        return True
    return isinstance(previous_revision, int) and revision < previous_revision


def persist_immutable(path: Path, data: bytes, label: str) -> None:
    if path.exists() and path.read_bytes() != data:
        raise BridgeError(f"Immutable {label} collision: {path}")
    if not path.exists():
        atomic_write(path, data)


def build_review_event(
    signal: dict[str, Any],
    review_path: Path,
    context: PersistenceContext,
) -> dict[str, Any]:
    artifact = relative_display(review_path, context.root)
    return {
        "schema": EVENT_SCHEMA,
        "capturedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "deviceSerial": context.device,
        "signal": signal,
        "reviewArtifact": artifact,
        "prompt": (
            f"Read {artifact} and process Tsuyomi live review revision {signal['revision']} "
            f"for {signal['nodeId']} on build {signal['buildId']}. "
            "Do not infer human approval from comment text."
        ),
    }


def persist_submission(
    signal: dict[str, Any],
    review_raw: bytes,
    context: PersistenceContext,
) -> dict[str, Any] | None:
    session_id = signal["sessionId"]
    revision = signal["revision"]
    digest = signal["reviewSha256"]
    state_path, state = load_bridge_state(context)
    key = f"{context.device}|{session_id}"
    if should_skip_revision(state, key, revision, digest):
        return None

    session_root = context.output_root / session_id
    stem = f"{revision:06d}__{digest[:12]}.json"
    review_path = session_root / "revisions" / stem
    event_path = session_root / "events" / stem
    persist_immutable(review_path, review_raw, "review artifact")

    event = build_review_event(signal, review_path, context)
    event_bytes = (json.dumps(event, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    persist_immutable(event_path, event_bytes, "event artifact")
    atomic_write(context.output_root / "latest.json", event_bytes)

    state["devices"][key] = {
        "revision": revision,
        "reviewSha256": digest,
        "buildId": signal["buildId"],
        "capturedAt": event["capturedAt"],
    }
    atomic_write(state_path, (json.dumps(state, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    return event


def capture_latest(args: argparse.Namespace, allow_missing: bool = False) -> dict[str, Any] | None:
    try:
        signal_raw = read_private_file(args.adb, args.device, args.package, SIGNAL_FILE)
        review_raw = read_private_file(args.adb, args.device, args.package, PAYLOAD_FILE)
    except BridgeError:
        if allow_missing:
            return None
        raise
    if not signal_raw.strip() or not review_raw.strip():
        if allow_missing:
            return None
        raise BridgeError("Live review signal or payload is empty")
    signal = parse_json_bytes(signal_raw, "signal")
    review = parse_json_bytes(review_raw, "review payload")
    policy = SubmissionPolicy(args.package, frozenset(args.active_profiles))
    persistence = PersistenceContext(args.device, args.output, args.root)
    validate_submission(signal, review, review_raw, policy)
    event = persist_submission(signal, review_raw, persistence)
    if event is not None:
        print(EVENT_PREFIX + json.dumps(event, ensure_ascii=False, separators=(",", ":")), flush=True)
    return event


def start_logcat(args: argparse.Namespace) -> subprocess.Popen[str]:
    return subprocess.Popen(
        [
            args.adb,
            "-s",
            args.device,
            "logcat",
            "-v",
            "raw",
            f"{LOG_TAG}:I",
            "*:S",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def consume_logcat(args: argparse.Namespace, process: subprocess.Popen[str]) -> int | None:
    assert process.stdout is not None
    for line in process.stdout:
        if not line.strip().startswith(LOG_MARKER):
            continue
        try:
            event = capture_latest(args)
        except BridgeError as error:
            print(f"review bridge capture rejected: {error}", file=sys.stderr, flush=True)
            continue
        if event is not None and args.once:
            return 0
    return None


def run_watch_session(args: argparse.Namespace) -> int | None:
    process = start_logcat(args)
    try:
        return consume_logcat(args, process)
    except KeyboardInterrupt:
        return 130
    finally:
        if process.poll() is None:
            process.terminate()
        if process.stderr is not None:
            detail = process.stderr.read().strip()
            if detail:
                print(f"review bridge logcat exited; retrying: {detail}", file=sys.stderr)


def watch(args: argparse.Namespace) -> int:
    startup_event = capture_latest(args, allow_missing=True)
    if startup_event is not None and args.once:
        return 0
    while True:
        result = run_watch_session(args)
        if result is not None:
            return result
        print("review bridge logcat exited; retrying", file=sys.stderr)
        time.sleep(1.0)


def latest(args: argparse.Namespace) -> int:
    path = args.output / "latest.json"
    if not path.exists():
        raise BridgeError(f"No captured live review exists under {args.output}")
    event = load_json_file(path, {})
    print(EVENT_PREFIX + json.dumps(event, ensure_ascii=False, separators=(",", ":")))
    return 0


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    root = monorepo_root()
    parser = argparse.ArgumentParser(description="Capture Tsuyomi in-app review submissions through ADB")
    parser.add_argument("--root", type=Path, default=root, help="Tsuyomi monorepo root")
    parser.add_argument("--output", type=Path, help="live review artifact directory")
    parser.add_argument("--adb", help="ADB executable path")
    parser.add_argument("--package", default=PACKAGE)
    subparsers = parser.add_subparsers(dest="command", required=True)

    pull_parser = subparsers.add_parser("pull", help="pull and validate the latest submission")
    pull_parser.add_argument("--device", required=True)

    watch_parser = subparsers.add_parser("watch", help="watch logcat and capture each new submission")
    watch_parser.add_argument("--device", required=True)
    watch_parser.add_argument("--once", action="store_true", help="exit after the first new submission")

    subparsers.add_parser("latest", help="print the latest captured event")

    args = parser.parse_args(argv)
    args.root = args.root.resolve()
    args.output = (args.output or args.root / ".local" / "ai-reviews" / "live").resolve()
    args.adb = resolve_adb(args.adb)
    args.active_profiles = load_active_profiles(args.root)
    return args


def main(argv: list[str] | None = None) -> int:
    try:
        args = parse_args(argv)
        if args.command == "pull":
            capture_latest(args)
            return 0
        if args.command == "watch":
            return watch(args)
        if args.command == "latest":
            return latest(args)
        raise BridgeError(f"Unsupported command: {args.command}")
    except BridgeError as error:
        print(f"review bridge error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
