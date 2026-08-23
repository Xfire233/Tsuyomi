# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import r1_change_detection as r1


class PrototypeBuildIdentityTest(unittest.TestCase):
    def test_local_outputs_do_not_change_prototype_build_id(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            android = root / "tsuyomi-android"
            prototype = android / "prototype" / "ui-atlas"
            (android / "docs" / "design").mkdir(parents=True)
            (android / "docs" / "design" / "UI_CONSTITUTION.md").write_text("contract", encoding="utf-8")
            prototype.mkdir(parents=True)
            (prototype / "build.gradle.kts").write_text(
                "val prototypeDataSchemaVersion = 1\n"
                "val prototypeReviewSchemaVersion = 2\n",
                encoding="utf-8",
            )
            stable = prototype / "src" / "main" / "Stable.kt"
            stable.parent.mkdir(parents=True)
            stable.write_text("stable", encoding="utf-8")

            expected = r1.compute_prototype_build_id(root)
            local_files = {
                "build/output.txt": "build",
                ".gradle/cache.bin": "cache",
                "src/screenshotTestDebug/reference/frame.png": "pixels",
                "tools/debug.py": "debug",
                "tools/__pycache__/debug.pyc": "bytecode",
                "tsuyomi-atlas-review-bundle-local.json": "export",
                "render-browser-atlas.bat": "browser",
                "local.properties": "sdk.dir=local",
            }
            for relative, content in local_files.items():
                path = prototype / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")

            self.assertEqual(expected, r1.compute_prototype_build_id(root))
            stable.write_text("changed", encoding="utf-8")
            self.assertNotEqual(expected, r1.compute_prototype_build_id(root))


if __name__ == "__main__":
    unittest.main()
