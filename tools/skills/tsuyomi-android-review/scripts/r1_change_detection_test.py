# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations
import json

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


class PhaseContractDetectionTest(unittest.TestCase):
    def test_phase_four_contract_expands_all_review_nodes(self) -> None:
        node_ids = ["L01", "B01", "S01", "M01", "X06"]

        category, selected, reasons = r1.classify_change(
            "tsuyomi-android/docs/phases/PHASE_4.md",
            node_ids,
        )

        self.assertEqual("contract", category)
        self.assertEqual(set(node_ids), selected)
        self.assertEqual(["binding UI/review contract changed"], reasons)

    def test_domain_classifiers_preserve_scope_precedence(self) -> None:
        node_ids = [
            "L01", "L08", "B01", "B03", "S01", "S04", "M02", "M03", "M04", "M05",
            "X01", "X02", "X03", "X04", "X05", "X06",
        ]
        cases = (
            (
                "tsuyomi-android/source/quickjs-runtime/src/main/kotlin/Runtime.kt",
                "runtime",
                {"S01", "S04", "B01", "L08", "X05"},
            ),
            (
                "tsuyomi-android/feature/library/src/androidTest/kotlin/LibraryTest.kt",
                "evidence",
                {"L01", "L08", "B01", "X01", "X02", "X04", "X05"},
            ),
            (
                "tools/skills/tsuyomi-android-review/scripts/review_bridge.py",
                "workflow",
                {"X06"},
            ),
        )

        for path, expected_category, expected_nodes in cases:
            with self.subTest(path=path):
                category, selected, _ = r1.classify_change(path, node_ids)
                self.assertEqual(expected_category, category)
                self.assertEqual(expected_nodes, selected)


    def test_review_export_schema_matches_catalog_version(self) -> None:
        root = r1.find_repo_root(Path.cwd())
        catalog_version, _ = r1.parse_catalog(root)
        schema_path = root / "tsuyomi-android/prototype/ui-atlas/review/interactive-review-export.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))

        self.assertEqual(
            catalog_version,
            schema["properties"]["build"]["properties"]["reviewCatalogVersion"]["const"],
        )
        catalog_item = schema["properties"]["reviewCatalog"]["items"]
        self.assertIn("evidenceStage", catalog_item["required"])
        self.assertEqual(
            ["atlas_ui", "actual_online_scenario"],
            catalog_item["properties"]["evidenceStage"]["enum"],
        )

if __name__ == "__main__":
    unittest.main()
