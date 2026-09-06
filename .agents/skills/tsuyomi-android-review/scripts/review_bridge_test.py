# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import review_bridge as bridge


class ReviewBridgeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.review = {
            "schema": bridge.REVIEW_SCHEMA,
            "provisional": True,
            "productionAuthorized": False,
            "build": {
                "applicationId": bridge.PACKAGE,
                "buildId": "a" * 64,
            },
            "reviewCatalog": [{"id": "L03"}],
        }
        self.review_raw = json.dumps(self.review, separators=(",", ":")).encode("utf-8")
        self.signal = {
            "schema": bridge.SIGNAL_SCHEMA,
            "sessionId": "123e4567-e89b-42d3-a456-426614174000",
            "revision": 7,
            "kind": "node",
            "applicationId": bridge.PACKAGE,
            "buildId": "a" * 64,
            "nodeId": "L03",
            "route": "library/history",
            "profile": "STANDARD",
            "reviewSha256": hashlib.sha256(self.review_raw).hexdigest(),
            "submittedAt": "2026-08-20T12:00:00Z",
        }
        self.policy = bridge.SubmissionPolicy(bridge.PACKAGE, frozenset({"STANDARD"}))

    def test_valid_submission(self) -> None:
        bridge.validate_submission(self.signal, self.review, self.review_raw, self.policy)

    def test_payload_hash_mismatch_is_rejected(self) -> None:
        changed = dict(self.signal, reviewSha256="b" * 64)
        with self.assertRaisesRegex(bridge.BridgeError, "hash mismatch"):
            bridge.validate_submission(changed, self.review, self.review_raw, self.policy)

    def test_deferred_profile_is_rejected(self) -> None:
        changed = dict(self.signal, profile="EINK")
        with self.assertRaisesRegex(bridge.BridgeError, "is not active"):
            bridge.validate_submission(changed, self.review, self.review_raw, self.policy)

    def test_persistence_is_content_addressed_and_deduplicated(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / ".local" / "ai-reviews" / "live"
            persistence = bridge.PersistenceContext("emulator-5554", output, root)
            event = bridge.persist_submission(self.signal, self.review_raw, persistence)
            self.assertIsNotNone(event)
            assert event is not None
            artifact = root / event["reviewArtifact"]
            self.assertEqual(self.review_raw, artifact.read_bytes())
            self.assertIsNone(
                bridge.persist_submission(self.signal, self.review_raw, persistence)
            )
            self.assertTrue((output / "latest.json").is_file())
            self.assertTrue((output / "bridge-state.json").is_file())

    def test_empty_startup_files_are_treated_as_no_submission(self) -> None:
        args = SimpleNamespace(
            adb="adb",
            device="emulator-5554",
            package=bridge.PACKAGE,
            active_profiles={"STANDARD"},
            output=Path("unused"),
            root=Path("unused"),
        )
        with patch.object(bridge, "read_private_file", side_effect=[b"", b""]):
            self.assertIsNone(bridge.capture_latest(args, allow_missing=True))


if __name__ == "__main__":
    unittest.main()
