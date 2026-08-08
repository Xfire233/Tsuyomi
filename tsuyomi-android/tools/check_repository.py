# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from pathlib import Path
import subprocess
import sys

FORBIDDEN_PARTS = {"build", ".gradle", ".kotlin", ".idea", ".externalNativeBuild", ".cxx"}
FORBIDDEN_NAMES = {"local.properties", "g", "id_rsa", "id_ed25519"}
FORBIDDEN_SUFFIXES = {".hprof", ".jks", ".keystore", ".p12", ".pem", ".key", ".apk", ".aab"}

result = subprocess.run(
    ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
    check=True,
    capture_output=True,
)
paths = [Path(value.decode("utf-8")) for value in result.stdout.split(b"\0") if value]
violations: list[str] = []
for path in paths:
    if any(part in FORBIDDEN_PARTS for part in path.parts):
        violations.append(str(path))
    elif path.name in FORBIDDEN_NAMES or path.suffix.lower() in FORBIDDEN_SUFFIXES:
        violations.append(str(path))
    elif path.name.startswith(".env") and path.name != ".env.example":
        violations.append(str(path))

if violations:
    print("Forbidden repository artifacts:", file=sys.stderr)
    for violation in sorted(violations):
        print(f"- {violation}", file=sys.stderr)
    raise SystemExit(1)

print(f"Repository artifact policy passed for {len(paths)} candidate files.")
