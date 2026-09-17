# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0
"""Generate the deterministic signed malformed-output HXP fixture.

Run: python source/extension-testkit/fixtures/generate_malformed_output_fixture.py
Check: python source/extension-testkit/fixtures/generate_malformed_output_fixture.py --check
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile, ZipInfo

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey


FIXTURE_DIRECTORY = Path(__file__).parent / "wenku8"
OUTPUT = FIXTURE_DIRECTORY / "malformed-output-fixture.hxp"
OUTPUT_DIGEST = FIXTURE_DIRECTORY / "malformed-output-fixture.sha256"
KEY_ID = "tsuyomi-phase2-fixture"
# The existing public fixture key's deterministic test-only seed; never a production trust key.
PRIVATE_SEED = bytes(range(1, 33))
ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)

ENTRY = b'''globalThis.tsuyomiExtension = {
  buildSearchRequest(mode) {
    return {
      url: `https://www.wenku8.net/fixture/search?mode=${encodeURIComponent(mode)}`,
      method: "GET",
      decode: "utf-8",
      cache: "network-only"
    };
  },
  classifyPage() {
    return "ok";
  },
  parseSearch(_html, finalUrl) {
    if (finalUrl.includes("mode=missing-required-field")) {
      return {
        items: [{
          sourceId: "org.tsuyomi.malformed-output",
          remoteBookId: "fixture-book",
          author: "Fixture author",
          canonicalUrl: "https://www.wenku8.net/book/fixture-book.htm"
        }]
      };
    }
    return [];
  }
};
'''


def canonical_json(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def archive_bytes() -> bytes:
    entry_digest = sha256(ENTRY)
    files = {"index.mjs": entry_digest}
    content_digest = sha256(canonical_json(files))
    manifest = {
        "format": "tsuyomi-hxp",
        "manifestVersion": 1,
        "id": "org.tsuyomi.malformed-output",
        "version": "1.0.0",
        "display": {
            "name": "Malformed source output fixture",
            "summary": "Signed deterministic malformed runtime output for host normalization regression.",
        },
        "hostApi": {"minInclusive": "1.2.0", "maxExclusive": "2.0.0"},
        "entry": "index.mjs",
        "integrity": {"algorithm": "sha256", "contentDigest": content_digest, "files": files},
        "signing": {"algorithm": "Ed25519", "keyId": KEY_ID, "signatureFile": "signature.ed25519"},
        "capabilities": {
            "network": {
                "origins": ["https://www.wenku8.net"],
                "maxConcurrentRequests": 1,
                "requestTimeoutMs": 1000,
                "maxResponseBytes": 1024,
            },
            "cookies": {"mode": "none", "origins": []},
            "webLogin": {"enabled": False, "origins": []},
            "remoteLibrary": {"read": False, "writeOperations": []},
            "storage": {"quotaBytes": 0},
        },
        "resourceLimits": {"maxExecutionWallTimeMs": 1000, "maxMemoryBytes": 1048576},
        "update": {"channel": "stable"},
    }
    manifest_bytes = canonical_json(manifest)
    signature = Ed25519PrivateKey.from_private_bytes(PRIVATE_SEED).sign(
        b"tsuyomi-hxp-v1\x00" + manifest_bytes + b"\x00" + content_digest.encode("ascii"),
    )
    entries = (("manifest.json", manifest_bytes), ("index.mjs", ENTRY), ("signature.ed25519", signature))
    output = io.BytesIO()
    with ZipFile(output, "w", compression=ZIP_STORED, strict_timestamps=True) as archive:
        for name, content in entries:
            entry = ZipInfo(name, date_time=ZIP_TIMESTAMP)
            entry.compress_type = ZIP_STORED
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, content)
    return output.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail unless committed fixture bytes and digest match")
    arguments = parser.parse_args()
    bytes_to_write = archive_bytes()
    digest_line = f"{sha256(bytes_to_write)}  {OUTPUT.name}\n"
    if arguments.check:
        if not OUTPUT.is_file() or not OUTPUT_DIGEST.is_file():
            raise SystemExit("Malformed-output fixture or checksum is missing; rerun without --check.")
        if OUTPUT.read_bytes() != bytes_to_write or OUTPUT_DIGEST.read_text(encoding="ascii") != digest_line:
            raise SystemExit("Malformed-output fixture is stale; rerun without --check.")
        return
    OUTPUT.write_bytes(bytes_to_write)
    OUTPUT_DIGEST.write_text(digest_line, encoding="ascii")


if __name__ == "__main__":
    main()
