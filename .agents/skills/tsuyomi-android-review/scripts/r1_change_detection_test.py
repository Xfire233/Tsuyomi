# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import r1_change_detection as r1


NODE_IDS = [
    "L01", "L08", "B01", "B02", "B03", "S01", "S02", "S03", "S04",
    "M02", "M03", "M04", "M05", "M07", "X01", "X02", "X03", "X04", "X05", "X06",
]


class ReviewBuildIdentityTest(unittest.TestCase):
    def test_only_production_inputs_and_active_contract_change_build_identity(self) -> None:
        files = {
            "tsuyomi-android/app/src/main/kotlin/App.kt": "app-v1",
            "tsuyomi-android/app/src/androidTest/kotlin/AppTest.kt": "test-v1",
            "tsuyomi-android/docs/design/UI_CONSTITUTION.md": "contract-v1",
            r1.CATALOG_PATH.as_posix(): "catalog-v1",
            "TOOLING.md": "tooling-v1",
        }

        expected = r1.compute_review_build_id(files)
        test_only = {**files, "tsuyomi-android/app/src/androidTest/kotlin/AppTest.kt": "test-v2"}
        tooling_only = {**files, "TOOLING.md": "tooling-v2"}
        production = {**files, "tsuyomi-android/app/src/main/kotlin/App.kt": "app-v2"}
        contract = {**files, "tsuyomi-android/docs/design/UI_CONSTITUTION.md": "contract-v2"}

        self.assertEqual(expected, r1.compute_review_build_id(test_only))
        self.assertEqual(expected, r1.compute_review_build_id(tooling_only))
        self.assertNotEqual(expected, r1.compute_review_build_id(production))
        self.assertNotEqual(expected, r1.compute_review_build_id(contract))


class ScopeClassificationTest(unittest.TestCase):
    def test_phase_four_contract_remains_full_scope(self) -> None:
        category, selected, reasons = r1.classify_change(
            "tsuyomi-android/docs/phases/PHASE_4.md",
            NODE_IDS,
        )

        self.assertEqual("contract", category)
        self.assertEqual(set(NODE_IDS), selected)
        self.assertEqual(["binding product or phase contract changed"], reasons)

    def test_review_catalog_change_remains_full_contract_scope(self) -> None:
        category, selected, reasons = r1.classify_change(r1.CATALOG_PATH.as_posix(), NODE_IDS)

        self.assertEqual("contract", category)
        self.assertEqual(set(NODE_IDS), selected)
        self.assertEqual(["review scope authority changed"], reasons)

    def test_historical_and_procedural_design_docs_select_review_system_only(self) -> None:
        for path in (
            "tsuyomi-android/docs/design/UI_ATLAS.md",
            "tsuyomi-android/docs/design/DESIGN_DIRECTION_HANDOFF.md",
            "tsuyomi-android/docs/design/DESIGN_REFERENCE_REVIEW.md",
        ):
            with self.subTest(path=path):
                category, selected, _ = r1.classify_change(path, NODE_IDS)
                self.assertEqual("workflow", category)
                self.assertEqual({"X06"}, selected)

    def test_domain_classifiers_select_only_named_surface_and_capabilities(self) -> None:
        cases = (
            (
                "tsuyomi-android/feature/book/src/main/kotlin/BookDetailModules.kt",
                "runtime",
                {"B01", "X04"},
            ),
            (
                "tsuyomi-android/app/src/main/kotlin/SourceRemoteLibraryCoordinator.kt",
                "runtime",
                {"L08", "S03", "B01", "X05"},
            ),
            (
                "tsuyomi-android/source/quickjs-runtime/src/main/kotlin/Runtime.kt",
                "runtime",
                {"S01", "S02", "S03", "S04", "B01", "L08", "X05"},
            ),
            (
                "tsuyomi-android/feature/library/src/androidTest/kotlin/LibraryTest.kt",
                "evidence",
                {"L01", "L08", "B01"},
            ),
            ("TOOLING.md", "workflow", {"X06"}),
            (
                "tsuyomi-android/feature/library/build.gradle.kts",
                "build",
                set(),
            ),
        )

        for path, expected_category, expected_nodes in cases:
            with self.subTest(path=path):
                category, selected, _ = r1.classify_change(path, NODE_IDS)
                self.assertEqual(expected_category, category)
                self.assertEqual(expected_nodes, selected)

    def test_unknown_android_source_stays_conservative(self) -> None:
        category, selected, reasons = r1.classify_change(
            "tsuyomi-android/new-area/src/main/kotlin/Unknown.kt",
            NODE_IDS,
        )

        self.assertEqual("runtime", category)
        self.assertEqual(set(NODE_IDS), selected)
        self.assertEqual(["unknown Android source change; conservative full scope"], reasons)


class GitBaselineTest(unittest.TestCase):
    def git(self, root: Path, *args: str) -> None:
        subprocess.run(
            ["git", *args],
            cwd=root,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def test_merge_base_fallback_includes_tracked_and_untracked_changes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            book = root / "tsuyomi-android/feature/book/src/main/kotlin/BookDetailModules.kt"
            search = root / "tsuyomi-android/feature/search/src/main/kotlin/SearchScreen.kt"
            book.parent.mkdir(parents=True)
            book.write_text("old", encoding="utf-8")
            (root / "WORKSPACE.md").write_text("workspace", encoding="utf-8")
            self.git(root, "init", "-b", "main")
            self.git(root, "config", "user.email", "test@example.invalid")
            self.git(root, "config", "user.name", "Test")
            self.git(root, "add", ".")
            self.git(root, "commit", "-m", "baseline")

            book.write_text("new", encoding="utf-8")
            search.parent.mkdir(parents=True)
            search.write_text("search", encoding="utf-8")
            current = {
                book.relative_to(root).as_posix(): r1.sha256_file(book),
                search.relative_to(root).as_posix(): r1.sha256_file(search),
            }

            baseline = r1.load_git_baseline(root, current, "main")

            self.assertIsNotNone(baseline)
            assert baseline is not None
            self.assertTrue(baseline.label.startswith("git:"))
            analysis = r1.detect_changes(baseline.files, current, NODE_IDS)
            self.assertEqual(
                {
                    "tsuyomi-android/feature/book/src/main/kotlin/BookDetailModules.kt",
                    "tsuyomi-android/feature/search/src/main/kotlin/SearchScreen.kt",
                },
                {item["path"] for item in analysis.changes},
            )
            self.assertEqual({"B01", "S02", "X04"}, set(analysis.affected_reasons))


class PolicyCatalogConsistencyTest(unittest.TestCase):
    def test_review_policy_covers_catalog_prefixes_without_overlap(self) -> None:
        root = r1.find_repo_root(Path.cwd())
        policy, _ = r1.load_review_policy(root)
        node_ids = r1.parse_catalog(root)[1]
        catalog_prefixes = {node_id[0] for node_id in node_ids}
        execution = policy["nodeExecution"]
        active_prefixes = set(execution["activeNodePrefixes"])
        deferred_prefixes = {
            prefix
            for stage in execution["deferredStages"]
            for prefix in stage["nodePrefixes"]
        }
        actual_online_prefixes = set(execution["actualOnlineRequirements"]["nodePrefixes"])

        self.assertTrue(policy["mode"])
        self.assertEqual(set(), active_prefixes & deferred_prefixes)
        self.assertEqual(catalog_prefixes, active_prefixes | deferred_prefixes)
        self.assertLessEqual(actual_online_prefixes, active_prefixes)
        self.assertEqual(
            set(),
            set(policy["activeProfiles"]) & {item["profile"] for item in policy["deferredProfiles"]},
        )

    def test_catalog_is_standalone_production_review_data(self) -> None:
        root = r1.find_repo_root(Path.cwd())
        catalog_version, node_ids = r1.parse_catalog(root)
        catalog = json.loads((root / r1.CATALOG_PATH).read_text(encoding="utf-8"))

        self.assertEqual(36, catalog_version)
        self.assertEqual(28, len(node_ids))
        self.assertEqual(
            {"production_ui", "actual_online_scenario"},
            {node["evidenceStage"] for node in catalog["nodes"]},
        )
        required = {
            "id", "title", "family", "kind", "route", "requiredStates",
            "operations", "visualChecks", "humanOnlyChecks", "evidenceStage",
        }
        self.assertTrue(all(required <= set(node) for node in catalog["nodes"]))

if __name__ == "__main__":
    unittest.main()
