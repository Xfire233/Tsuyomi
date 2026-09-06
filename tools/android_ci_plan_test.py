# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import tools.android_ci_plan as planner


REPO_ROOT = Path(__file__).resolve().parents[1]

def modules_with_sources(source_set: str) -> set[str]:
    android_root = REPO_ROOT / "tsuyomi-android"
    modules = set()
    for source in android_root.glob(f"**/src/{source_set}/**/*.kt"):
        relative = source.relative_to(android_root)
        module_parts = relative.parts[: relative.parts.index("src")]
        modules.add(":" + ":".join(module_parts))
    return modules


def unit_task(module: str) -> str:
    return f"{module}:testDebugUnitTest" if planner.is_android_module(REPO_ROOT, module) else f"{module}:test"


class AndroidCiPlanTest(unittest.TestCase):
    def test_documentation_only_change_skips_android_jobs(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/docs/process/QUALITY_GATES.md"],
        )

        self.assertFalse(plan.changed)
        self.assertEqual((), plan.build_tasks)
        self.assertEqual((), plan.instrumentation_tasks)


    def test_book_ui_change_selects_book_and_app_consumers(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/feature/book/src/main/kotlin/org/tsuyomi/feature/book/BookDetailModules.kt"],
        )

        self.assertEqual(
            {":app:assembleDebug", ":app:testDebugUnitTest", ":feature:book:lintDebug"},
            set(plan.build_tasks),
        )
        self.assertEqual(
            (":app:connectedDebugAndroidTest", ":feature:book:connectedDebugAndroidTest"),
            plan.instrumentation_tasks,
        )

    def test_library_ui_change_adds_library_and_app_proof(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/feature/library/src/main/kotlin/org/tsuyomi/feature/library/LibraryScreen.kt"],
        )

        self.assertEqual(
            {
                ":app:assembleDebug",
                ":app:testDebugUnitTest",
                ":feature:library:lintDebug",
                ":feature:library:testDebugUnitTest",
                ":feature:library:validateDebugScreenshotTest",
            },
            set(plan.build_tasks),
        )
        self.assertEqual(
            (":app:connectedDebugAndroidTest", ":feature:library:connectedDebugAndroidTest"),
            plan.instrumentation_tasks,
        )

    def test_unit_test_change_does_not_build_or_start_emulator(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/source/extension-manager/src/test/kotlin/InstallerTest.kt"],
        )

        self.assertEqual(
            (":source:extension-manager:testDebugUnitTest",),
            plan.build_tasks,
        )
        self.assertEqual((), plan.instrumentation_tasks)

    def test_dependency_input_uses_separate_lock_path(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/feature/library/gradle.lockfile"],
        )

        self.assertTrue(plan.dependency_changed)
        self.assertIn(":app:assembleDebug", plan.build_tasks)
        self.assertIn(":feature:library:lintDebug", plan.build_tasks)

    def test_root_gradle_dependency_input_selects_lock_check(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/build.gradle.kts"],
        )

        self.assertTrue(plan.dependency_changed)
        self.assertEqual(planner.FULL_BUILD_TASKS, plan.build_tasks)

    def test_shared_build_input_retains_conservative_full_gate(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/build-logic/src/main/kotlin/AndroidPlugin.kt"],
        )

        self.assertTrue(plan.production_changed)
        self.assertEqual(planner.FULL_BUILD_TASKS, plan.build_tasks)
        self.assertEqual(planner.FULL_INSTRUMENTATION_TASKS, plan.instrumentation_tasks)

    def test_test_bearing_modules_are_covered_by_full_gate(self) -> None:
        expected_unit = {unit_task(module) for module in modules_with_sources("test")}
        expected_instrumentation = {
            f"{module}:connectedDebugAndroidTest" for module in modules_with_sources("androidTest")
        }
        expected_screenshot = {
            f"{module}:validateDebugScreenshotTest" for module in modules_with_sources("screenshotTest")
        }

        self.assertTrue(expected_unit <= set(planner.FULL_BUILD_TASKS))
        self.assertTrue(expected_instrumentation <= set(planner.FULL_INSTRUMENTATION_TASKS))
        self.assertTrue(expected_screenshot <= set(planner.FULL_BUILD_TASKS))

    def test_source_contract_change_runs_own_unit_test_and_app_integration(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/shared/source-contract/src/main/kotlin/org/tsuyomi/shared/sourcecontract/SourceContracts.kt"],
        )

        self.assertIn(":shared:source-contract:test", plan.build_tasks)
        self.assertIn(":feature:library:validateDebugScreenshotTest", plan.build_tasks)
        self.assertIn(":feature:browse:validateDebugScreenshotTest", plan.build_tasks)
        self.assertIn(":app:connectedDebugAndroidTest", plan.instrumentation_tasks)

    def test_preferences_change_runs_settings_visual_and_app_integration(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/core/preferences/src/main/kotlin/org/tsuyomi/core/preferences/InterfacePreferencesResetter.kt"],
        )

        self.assertIn(":feature:settings:validateDebugScreenshotTest", plan.build_tasks)
        self.assertIn(":app:connectedDebugAndroidTest", plan.instrumentation_tasks)

    def test_browse_change_runs_module_and_app_integration(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/feature/browse/src/main/kotlin/org/tsuyomi/feature/browse/BrowseScreen.kt"],
        )

        self.assertEqual(
            (":app:connectedDebugAndroidTest", ":feature:browse:connectedDebugAndroidTest"),
            plan.instrumentation_tasks,
        )

    def test_reader_ui_change_runs_own_instrumentation(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/reader/ui/src/main/kotlin/org/tsuyomi/reader/ui/ReaderSurface.kt"],
        )

        self.assertIn(":reader:ui:lintDebug", plan.build_tasks)
        self.assertEqual(
            (":app:connectedDebugAndroidTest", ":reader:ui:connectedDebugAndroidTest"),
            plan.instrumentation_tasks,
        )


    def test_reader_engine_change_runs_reverse_consumer_tests(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/reader/engine/src/main/kotlin/org/tsuyomi/reader/engine/ReaderDocumentSession.kt"],
        )

        self.assertIn(":reader:engine:test", plan.build_tasks)
        self.assertIn(":app:testDebugUnitTest", plan.build_tasks)
        self.assertIn(":reader:ui:connectedDebugAndroidTest", plan.instrumentation_tasks)
        self.assertIn(":app:connectedDebugAndroidTest", plan.instrumentation_tasks)

    def test_shared_model_change_runs_reverse_consumer_tests(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/shared/model/src/main/kotlin/org/tsuyomi/shared/model/BookIdentity.kt"],
        )

        self.assertIn(":shared:model:test", plan.build_tasks)
        self.assertIn(":feature:library:validateDebugScreenshotTest", plan.build_tasks)
        self.assertIn(":core:database:connectedDebugAndroidTest", plan.instrumentation_tasks)
        self.assertIn(":feature:book:connectedDebugAndroidTest", plan.instrumentation_tasks)
        self.assertIn(":reader:ui:connectedDebugAndroidTest", plan.instrumentation_tasks)
        self.assertIn(":app:connectedDebugAndroidTest", plan.instrumentation_tasks)

    def test_planner_change_forces_conservative_full_gate(self) -> None:
        plan = planner.plan_for_paths(REPO_ROOT, ["tools/android_ci_plan.py"])

        self.assertEqual(planner.FULL_BUILD_TASKS, plan.build_tasks)
        self.assertEqual(planner.FULL_INSTRUMENTATION_TASKS, plan.instrumentation_tasks)

    def test_invalid_git_base_forces_full_gate(self) -> None:
        paths, force_full = planner.git_paths(REPO_ROOT, "not-a-real-commit", "HEAD")

        self.assertEqual([], paths)
        self.assertTrue(force_full)
        plan = planner.plan_for_paths(REPO_ROOT, paths, force_full=force_full)
        self.assertEqual(planner.FULL_BUILD_TASKS, plan.build_tasks)

    def test_github_output_is_single_line_per_field(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/feature/book/src/main/kotlin/BookDetailModules.kt"],
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "github-output.txt"
            planner.write_github_output(path, plan.as_outputs())

            lines = path.read_text(encoding="utf-8").splitlines()

        self.assertEqual(set(plan.as_outputs()), {line.split("=", 1)[0] for line in lines})


if __name__ == "__main__":
    unittest.main()
