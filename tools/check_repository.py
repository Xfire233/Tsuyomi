# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from pathlib import Path
import subprocess
import sys

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

def is_public_hxp_fixture(path: Path) -> bool:
    return path.suffix.lower() == ".hxp" and path.parts[:3] == ("tsuyomi-extensions", "fixtures", "wenku8")

result = subprocess.run(
    ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
    check=True,
    capture_output=True,
)
paths = [Path(value.decode("utf-8")) for value in result.stdout.split(b"\0") if value]
violations = [
    str(path)
    for path in paths
    if any(part in FORBIDDEN_PARTS for part in path.parts)
    or path.name in FORBIDDEN_NAMES
    or (path.suffix.lower() in FORBIDDEN_SUFFIXES and not is_public_hxp_fixture(path))
    or path.name.endswith(".prompt.md")
    or ".transcript." in path.name
    or (path.name.startswith(".env") and path.name != ".env.example")
]

if violations:
    print("Forbidden repository artifacts:", file=sys.stderr)
    for violation in sorted(violations):
        print(f"- {violation}", file=sys.stderr)
    raise SystemExit(1)

print(f"Repository artifact policy passed for {len(paths)} candidate files.")
