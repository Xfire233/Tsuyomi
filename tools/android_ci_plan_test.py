# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import tools.android_ci_plan as planner


REPO_ROOT = Path(__file__).resolve().parents[1]


class AndroidCiPlanTest(unittest.TestCase):
    def test_documentation_only_change_skips_android_jobs(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/docs/process/QUALITY_GATES.md"],
        )

        self.assertFalse(plan.changed)
        self.assertEqual((), plan.build_tasks)
        self.assertEqual((), plan.instrumentation_tasks)


    def test_book_ui_change_selects_book_module_only(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/feature/book/src/main/kotlin/org/tsuyomi/feature/book/BookDetailModules.kt"],
        )

        self.assertEqual(
            {":app:assembleDebug", ":feature:book:lintDebug"},
            set(plan.build_tasks),
        )
        self.assertEqual(
            (":feature:book:connectedDebugAndroidTest",),
            plan.instrumentation_tasks,
        )

    def test_library_ui_change_adds_only_library_visual_and_device_proof(self) -> None:
        plan = planner.plan_for_paths(
            REPO_ROOT,
            ["tsuyomi-android/feature/library/src/main/kotlin/org/tsuyomi/feature/library/LibraryScreen.kt"],
        )

        self.assertEqual(
            {
                ":app:assembleDebug",
                ":feature:library:lintDebug",
                ":feature:library:validateDebugScreenshotTest",
            },
            set(plan.build_tasks),
        )
        self.assertEqual(
            (":feature:library:connectedDebugAndroidTest",),
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
