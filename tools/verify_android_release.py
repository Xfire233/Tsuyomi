#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0
"""Verify one published Android release artifact without holding the signing key.

The workflow downloads the uploaded asset and runs this verifier. Every fact is
re-derived from the artifact itself; the release body only has to agree. The
signer certificate is pinned here so a release body can never certify itself.

See tsuyomi-android/docs/process/RELEASE_PROCEDURE.md.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

APPLICATION_ID = "org.tsuyomi.android"
DEFAULT_CERTIFICATE_SHA256 = "0be46968ea9f184b8a5857334d4e4d46eb67ea14b0601c07a5fa3b07f00a7bb7"
MIN_SDK = 29

TAG_PREFIX = "android-v"
REQUIRED_MARKERS = (
    "apk-asset",
    "apk-sha256",
    "apk-bytes",
    "apk-version-code",
    "signer-certificate-sha256",
    "source-revision",
)
HEX40 = re.compile(r"^[0-9a-f]{40}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")

SCHEME_MARKERS = {
    "v1": "Verified using v1 scheme (JAR signing): true",
    "v2": "Verified using v2 scheme (APK Signature Scheme v2): true",
    "v3": "Verified using v3 scheme (APK Signature Scheme v3): true",
    "v4": "Verified using v4 scheme (APK Signature Scheme v4): true",
}


class VerificationError(Exception):
    """A published artifact failed an acceptance assertion."""


def parse_markers(release_body: str) -> dict[str, str]:
    markers: dict[str, str] = {}
    for line in release_body.splitlines():
        stripped = line.strip().lstrip("-*").strip()
        match = re.fullmatch(r"([a-z0-9-]+):\s*(\S+)", stripped)
        if match is None:
            continue
        key, value = match.group(1), match.group(2)
        if key in REQUIRED_MARKERS:
            if key in markers and markers[key] != value:
                raise VerificationError(f"release body declares {key} twice with different values")
            markers[key] = value
    missing = [key for key in REQUIRED_MARKERS if key not in markers]
    if missing:
        raise VerificationError(f"release body is missing required markers: {', '.join(missing)}")
    return markers


def version_name_from_tag(tag: str) -> str:
    if not tag.startswith(TAG_PREFIX):
        raise VerificationError(f"tag {tag!r} does not use the {TAG_PREFIX}<version> form")
    version = tag[len(TAG_PREFIX):]
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", version):
        raise VerificationError(f"tag {tag!r} does not carry a Semantic Version")
    return version


def resolve_build_tools(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit)
    roots = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        str(Path.home() / "Android" / "Sdk"),
        os.environ.get("LOCALAPPDATA") and str(Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk"),
    ]
    candidates: list[tuple[tuple[int, ...], Path]] = []
    for root in roots:
        if not root:
            continue
        build_tools = Path(root) / "build-tools"
        if not build_tools.is_dir():
            continue
        for entry in build_tools.iterdir():
            if not entry.is_dir() or not re.fullmatch(r"\d+(\.\d+)*", entry.name):
                continue
            candidates.append((tuple(int(part) for part in entry.name.split(".")), entry))
    if not candidates:
        raise VerificationError("no Android build-tools revision was found; pass --build-tools")
    return max(candidates)[1]


def run(command: list[str]) -> str:
    completed = subprocess.run(
        command, capture_output=True, text=True, encoding="utf-8", errors="replace", check=False
    )
    output = (completed.stdout or "") + (completed.stderr or "")
    if completed.returncode != 0:
        raise VerificationError(f"command failed ({completed.returncode}): {' '.join(command)}\n{output}")
    return output


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def verify(
    apk: Path,
    release_body: str,
    tag: str,
    build_tools: Path,
    certificate: str = DEFAULT_CERTIFICATE_SHA256,
    min_sdk: int = MIN_SDK,
) -> list[str]:
    if not apk.is_file():
        raise VerificationError(f"release asset is unavailable: {apk}")

    version_name = version_name_from_tag(tag)
    markers = parse_markers(release_body)

    if markers["apk-asset"] != apk.name:
        raise VerificationError(f"release body names {markers['apk-asset']!r} but the asset is {apk.name!r}")
    if not HEX64.fullmatch(markers["apk-sha256"]):
        raise VerificationError("apk-sha256 is not a SHA-256 digest")
    if not HEX64.fullmatch(markers["signer-certificate-sha256"]):
        raise VerificationError("signer-certificate-sha256 is not a SHA-256 digest")
    if markers["signer-certificate-sha256"] != certificate:
        raise VerificationError("release body does not declare the pinned release-signing certificate")
    if not HEX40.fullmatch(markers["source-revision"]):
        raise VerificationError("source-revision is not a Git object name")

    actual_digest = digest(apk)
    if actual_digest != markers["apk-sha256"]:
        raise VerificationError(f"asset digest {actual_digest} does not match the published {markers['apk-sha256']}")
    actual_bytes = apk.stat().st_size
    if str(actual_bytes) != markers["apk-bytes"]:
        raise VerificationError(f"asset size {actual_bytes} does not match the published {markers['apk-bytes']}")

    java = os.environ.get("JAVA_HOME")
    java_executable = str(Path(java) / "bin" / "java") if java else "java"
    apksigner = build_tools / "lib" / "apksigner.jar"
    zipalign = build_tools / ("zipalign.exe" if os.name == "nt" else "zipalign")
    aapt = build_tools / ("aapt2.exe" if os.name == "nt" else "aapt2")
    for tool in (apksigner, zipalign, aapt):
        if not tool.is_file():
            raise VerificationError(f"required verification tool is unavailable: {tool}")

    verification = run(
        [java_executable, "-jar", str(apksigner), "verify", "--verbose", "--print-certs",
         "--min-sdk-version", str(min_sdk), str(apk)]
    )
    digest_lines = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", verification)
    if certificate not in {line.lower() for line in digest_lines}:
        raise VerificationError(
            "published artifact is not signed by the pinned release identity\n"
            f"apksigner reported: {verification}"
        )
    schemes = {name: marker in verification for name, marker in SCHEME_MARKERS.items()}
    if schemes["v1"] or schemes["v2"] or not schemes["v3"] or schemes["v4"]:
        raise VerificationError(f"published artifact uses unexpected signature schemes: {schemes}")

    run([str(zipalign), "-c", "-P", "16", "4", str(apk)])

    badging = run([str(aapt), "dump", "badging", str(apk)])
    identity = re.search(
        r"^package: name='(?P<name>[^']+)' versionCode='(?P<code>\d+)' versionName='(?P<version>[^']+)'",
        badging,
        re.MULTILINE,
    )
    if identity is None:
        raise VerificationError("published artifact has no readable package identity")
    if identity.group("name") != APPLICATION_ID:
        raise VerificationError(f"published artifact declares application id {identity.group('name')!r}")
    if identity.group("version") != version_name:
        raise VerificationError(
            f"published artifact versionName {identity.group('version')!r} does not match tag {tag!r}"
        )
    if identity.group("code") != markers["apk-version-code"]:
        raise VerificationError("published artifact versionCode does not match the published value")
    if re.search(r"^application-debuggable$", badging, re.MULTILINE):
        raise VerificationError("published artifact is debuggable")
    if re.search(r"^application-testOnly", badging, re.MULTILINE):
        raise VerificationError("published artifact is test-only")

    with zipfile.ZipFile(apk) as archive:
        embedded = [name for name in archive.namelist() if name.startswith("assets/") and name.endswith(".hxp")]
    if embedded:
        raise VerificationError(f"published artifact embeds source-test fixtures: {embedded}")

    return [
        f"asset {apk.name}",
        f"sha256 {actual_digest}",
        f"bytes {actual_bytes}",
        f"versionName {version_name}",
        f"versionCode {identity.group('code')}",
        f"signer {certificate}",
        "signature schemes v3 only",
        "16KiB aligned",
        "not debuggable or test-only",
        "no embedded .hxp assets",
        f"source revision {markers['source-revision']}",
    ]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--release-body", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--build-tools")
    parser.add_argument("--certificate", default=DEFAULT_CERTIFICATE_SHA256)
    parser.add_argument("--min-sdk", type=int, default=MIN_SDK)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        facts = verify(
            apk=Path(args.apk),
            release_body=Path(args.release_body).read_text(encoding="utf-8"),
            tag=args.tag,
            build_tools=resolve_build_tools(args.build_tools),
            certificate=args.certificate,
            min_sdk=args.min_sdk,
        )
    except VerificationError as error:
        print(f"Release verification failed: {error}", file=sys.stderr)
        return 1
    print("Published release artifact verified:")
    for fact in facts:
        print(f"- {fact}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
