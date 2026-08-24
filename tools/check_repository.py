# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
import sys
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parents[1]
SCOPE_ROOTS = {
    "all": None,
    "android": Path("tsuyomi-android"),
    "protocol": Path("tsuyomi-protocol"),
    "extensions": Path("tsuyomi-extensions"),
}
FORBIDDEN_PARTS = {
    "build",
    "dist",
    "coverage",
    "node_modules",
    ".gradle",
    ".kotlin",
    ".idea",
    ".externalNativeBuild",
    ".cxx",
    ".local",
    ".claude",
    ".ai",
    ".agents",
    ".cursor",
    ".windsurf",
}
FORBIDDEN_NAMES = {
    "local.properties",
    "keystore.properties",
    ".mcp.json",
    "AGENTS.md",
    "AGENTS.local.md",
    "g",
    "id_rsa",
    "id_ed25519",
}
FORBIDDEN_SUFFIXES = {
    ".hprof",
    ".jks",
    ".keystore",
    ".p12",
    ".pem",
    ".key",
    ".apk",
    ".aab",
    ".hxp",
    ".bundle",
    ".log",
    ".trace",
}


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Reject local or sensitive repository artifacts")
    parser.add_argument("--scope", choices=tuple(SCOPE_ROOTS), default="all")
    return parser.parse_args(argv)


def git_visible_paths() -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(REPO_ROOT), "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        check=True,
        capture_output=True,
    )
    return [Path(value.decode("utf-8")) for value in result.stdout.split(b"\0") if value]


def paths_in_scope(paths: Iterable[Path], scope: str) -> list[Path]:
    root = SCOPE_ROOTS[scope]
    if root is None:
        return list(paths)
    return [path for path in paths if path.is_relative_to(root)]


def is_public_hxp_fixture(path: Path) -> bool:
    return path.suffix.lower() == ".hxp" and path.parts[:3] == ("tsuyomi-extensions", "fixtures", "wenku8")


def violates_policy(path: Path) -> bool:
    return (
        any(part in FORBIDDEN_PARTS for part in path.parts)
        or path.name in FORBIDDEN_NAMES
        or (path.suffix.lower() in FORBIDDEN_SUFFIXES and not is_public_hxp_fixture(path))
        or path.name.endswith(".prompt.md")
        or ".transcript." in path.name
        or (path.name.startswith(".env") and path.name != ".env.example")
    )


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    paths = paths_in_scope(git_visible_paths(), args.scope)
    violations = sorted(str(path) for path in paths if violates_policy(path))
    if violations:
        print(f"Forbidden repository artifacts in scope {args.scope}:", file=sys.stderr)
        for violation in violations:
            print(f"- {violation}", file=sys.stderr)
        return 1
    print(f"Repository artifact policy passed for {len(paths)} candidate files in scope {args.scope}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
