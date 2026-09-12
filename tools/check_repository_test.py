# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from pathlib import Path
import json
import tempfile
import unittest

from tools import check_repository


class RepositoryPolicyTest(unittest.TestCase):
    def test_component_scopes_select_only_the_requested_tree(self) -> None:
        paths = [
            Path("tsuyomi-android/app/src/main/kotlin/App.kt"),
            Path("tsuyomi-protocol/schemas/host-api.json"),
            Path("README.md"),
        ]

        self.assertEqual([paths[0]], check_repository.paths_in_scope(paths, "android"))
        self.assertEqual([paths[1]], check_repository.paths_in_scope(paths, "protocol"))
        self.assertEqual(paths, check_repository.paths_in_scope(paths, "all"))

    def test_only_the_public_wenku8_hxp_fixture_is_allowed(self) -> None:
        self.assertFalse(
            check_repository.violates_policy(Path("tsuyomi-android/source/extension-testkit/fixtures/wenku8/wenku8-fixture.hxp"))
        )
        self.assertTrue(check_repository.violates_policy(Path("tsuyomi-android/source/extension-testkit/fixtures/wenku8/private.hxp")))
        self.assertTrue(check_repository.violates_policy(Path("tsuyomi-android/source/extension-testkit/fixtures/other/wenku8-fixture.hxp")))

    def test_versioned_project_skills_are_the_only_agents_exception(self) -> None:
        self.assertFalse(
            check_repository.violates_policy(Path(".agents/skills/tsuyomi-android-review/SKILL.md"))
        )
        self.assertTrue(check_repository.violates_policy(Path(".agents/session.json")))
        self.assertTrue(
            check_repository.violates_policy(Path(".agents/skills/example/node_modules/dependency.js"))
        )

    def test_local_agent_and_sensitive_artifacts_are_rejected(self) -> None:
        rejected = [
            Path("tsuyomi-android/.local/report.json"),
            Path("tsuyomi-protocol/AGENTS.md"),
            Path("tsuyomi-protocol/.env.production"),
            Path("tsuyomi-android/release.jks"),
            Path("session.transcript.json"),
        ]

        self.assertTrue(all(check_repository.violates_policy(path) for path in rejected))
        self.assertFalse(check_repository.violates_policy(Path("tsuyomi-protocol/.env.example")))

    def test_retired_android_prototype_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            retired = root / "tsuyomi-android/prototype/ui-atlas"
            retired.mkdir(parents=True)
            settings = root / "tsuyomi-android/settings.gradle.kts"
            settings.write_text('include(":prototype:ui-atlas")', encoding="utf-8")
            source = root / "tsuyomi-android/app/src/main/kotlin/App.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package org.tsuyomi.prototype.app", encoding="utf-8")

            violations = check_repository.retired_android_prototype_violations(root)

        self.assertTrue(any("retired prototype must not exist" in item for item in violations))
        self.assertTrue(any("includes retired prototype module" in item for item in violations))
        self.assertTrue(any("references retired prototype package" in item for item in violations))

    def test_live_repository_has_no_retired_android_prototype(self) -> None:
        self.assertEqual([], check_repository.retired_android_prototype_violations())

    def test_frozen_eink_profile_rejects_registered_screenshot_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            policy = root / ".agents/skills/tsuyomi-android-review/review-policy.json"
            policy.parent.mkdir(parents=True)
            policy.write_text(
                json.dumps({"deferredProfiles": [{"profile": "EINK", "status": "FROZEN"}]}),
                encoding="utf-8",
            )
            preview = root / "tsuyomi-android/feature/example/src/screenshotTest/kotlin/EInkPreview.kt"
            preview.parent.mkdir(parents=True)
            preview.write_text(
                '@PreviewTest\n@Preview(name = "eink")\n@Composable\nfun EInkScreenshot() = Unit',
                encoding="utf-8",
            )

            violations = check_repository.frozen_profile_screenshot_violations(root)
            preview.write_text(
                '@Preview(name = "eink")\n@Composable\nfun EInkScreenshot() = Unit',
                encoding="utf-8",
            )
            restored = check_repository.frozen_profile_screenshot_violations(root)

        self.assertEqual(1, len(violations))
        self.assertEqual([], restored)

    def test_live_repository_has_no_frozen_profile_screenshot_evidence(self) -> None:
        self.assertEqual([], check_repository.frozen_profile_screenshot_violations())

    def test_frozen_eink_profile_requires_ignored_instrumentation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            policy = root / ".agents/skills/tsuyomi-android-review/review-policy.json"
            policy.parent.mkdir(parents=True)
            policy.write_text(
                json.dumps({"deferredProfiles": [{"profile": "EINK", "status": "FROZEN"}]}),
                encoding="utf-8",
            )
            test = root / "tsuyomi-android/app/src/androidTest/kotlin/EInkJourneyTest.kt"
            test.parent.mkdir(parents=True)
            test.write_text(
                "@Test\nfun e_ink_journey() { use(DisplayPreference.EINK) }",
                encoding="utf-8",
            )

            violations = check_repository.frozen_profile_instrumentation_violations(root)
            test.write_text(
                '@Ignore("frozen profile")\n@Test\nfun e_ink_journey() { use(DisplayPreference.EINK) }',
                encoding="utf-8",
            )
            ignored = check_repository.frozen_profile_instrumentation_violations(root)

        self.assertEqual(1, len(violations))
        self.assertEqual([], ignored)

    def test_live_repository_has_no_frozen_profile_instrumentation(self) -> None:
        self.assertEqual([], check_repository.frozen_profile_instrumentation_violations())

class ToolingGovernanceTest(unittest.TestCase):
    def create_valid_registry(self, root: Path) -> Path:
        tooling = root / "TOOLING.md"
        scope_rows = tuple(f"| {resource} | scope | complete |" for resource in check_repository.TOOLING_SCOPE_RESOURCES)
        native_rows = tuple(
            f"| {resource} | owner | trigger | preconditions | method | output | exclude | fallback | health |"
            for resource in check_repository.TOOLING_NATIVE_RESOURCES
        )
        skill_rows = tuple(
            f"| {resource} | owner | trigger | preconditions | method | output | exclude | fallback | health |"
            for resource in check_repository.TOOLING_SKILL_RESOURCES
        )
        mcp_rows = tuple(
            f"| {resource} | owner | trigger | preconditions | method | output | exclude | fallback | health |"
            for resource in check_repository.TOOLING_MCP_RESOURCES
        )
        sections = check_repository.TOOLING_REQUIRED_SECTIONS
        markers = check_repository.TOOLING_REQUIRED_MARKERS
        tooling.write_text(
            "\n".join(
                (
                    sections[0], markers[0], markers[1], markers[2],
                    sections[1],
                    sections[2],
                    sections[3], markers[3], *scope_rows,
                    sections[4], markers[4], *native_rows,
                    sections[5], markers[5], *skill_rows,
                    sections[6], markers[6], *mcp_rows,
                    sections[7], sections[8],
                    ".agents/skills/example",
                )
            ),
            encoding="utf-8",
        )
        documentation = root / "DOCUMENTATION.md"
        document_rows = (
            "| `TOOLING.md` | purpose | trigger | method | scope | complete | active |",
            "| `DOCUMENTATION.md` | purpose | trigger | method | scope | complete | active |",
            "| `.agents/skills/example/SKILL.md` | purpose | trigger | method | scope | complete | active |",
        )
        documentation.write_text(
            "\n".join(
                (*check_repository.DOCUMENTATION_REQUIRED_SECTIONS, *check_repository.DOCUMENTATION_REQUIRED_MARKERS, *document_rows)
            ),
            encoding="utf-8",
        )
        skill = root / ".agents" / "skills" / "example" / "SKILL.md"
        skill.parent.mkdir(parents=True)
        skill.write_text(
            "---\nname: example\ndescription: Example.\n---\n"
            "## When to use\nUse it.\n## Do not use\nDo not misuse it.\n"
            "## Required inputs\nRead inputs.\n",
            encoding="utf-8",
        )
        (skill.parent / "review-policy.json").write_text(
            json.dumps(
                {
                    "mode": "runtime-mode",
                    "phase4Milestone": "runtime milestone",
                    "nodeExecution": {"activeStage": "RUNTIME_STAGE"},
                }
            ),
            encoding="utf-8",
        )
        for relative_runner_path, required_markers in check_repository.GRADLE_RESOURCE_RUNNERS:
            resource_runner = root / relative_runner_path
            resource_runner.parent.mkdir(parents=True, exist_ok=True)
            resource_runner.write_text("\n".join(required_markers), encoding="utf-8")
        return skill

    def test_valid_project_skill_registry_passes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            self.assertEqual([], check_repository.tooling_governance_violations(root))

    def test_gradle_resource_runner_requires_both_modes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            high_runner_path, _ = check_repository.GRADLE_RESOURCE_RUNNERS[1]
            runner = root / high_runner_path
            runner.write_text(
                runner.read_text(encoding="utf-8").replace("--parallel", ""),
                encoding="utf-8",
            )
            violations = check_repository.tooling_governance_violations(root)
            self.assertTrue(any("missing required resource mode marker" in violation for violation in violations))

    def test_every_tool_requires_scope_and_completion(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            tooling = root / "TOOLING.md"
            tooling.write_text(
                tooling.read_text(encoding="utf-8").replace("| `read` | scope | complete |", ""),
                encoding="utf-8",
            )
            violations = check_repository.tooling_governance_violations(root)
            self.assertTrue(any("`read` must have exactly one scope/completion row" in violation for violation in violations))

    def test_each_tool_family_requires_one_complete_record(self) -> None:
        cases = (
            (
                "| `read` | owner | trigger | preconditions | method | output | exclude | fallback | health |",
                "`read` must have exactly one native tool record",
            ),
            (
                "| `android-cli` | owner | trigger | preconditions | method | output | exclude | fallback | health |",
                "`android-cli` must have exactly one Skill record",
            ),
            (
                "| `context7` | owner | trigger | preconditions | method | output | exclude | fallback | health |",
                "`context7` must have exactly one MCP record",
            ),
        )
        for row, expected in cases:
            with self.subTest(resource=expected):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    self.create_valid_registry(root)
                    tooling = root / "TOOLING.md"
                    tooling.write_text(tooling.read_text(encoding="utf-8").replace(row, ""), encoding="utf-8")
                    violations = check_repository.tooling_governance_violations(root)
                    self.assertTrue(any(expected in violation for violation in violations))

    def test_native_tool_record_requires_every_field(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            tooling = root / "TOOLING.md"
            tooling.write_text(
                tooling.read_text(encoding="utf-8").replace(
                    "| `read` | owner | trigger | preconditions | method | output | exclude | fallback | health |",
                    "| `read` | owner | | preconditions | method | output | exclude | fallback | health |",
                ),
                encoding="utf-8",
            )
            violations = check_repository.tooling_governance_violations(root)
            self.assertTrue(any("`read` native tool record is incomplete" in violation for violation in violations))

    def test_skill_name_must_match_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skill = self.create_valid_registry(root)
            skill.write_text(skill.read_text(encoding="utf-8").replace("name: example", "name: other"), encoding="utf-8")
            violations = check_repository.tooling_governance_violations(root)
            self.assertTrue(any("must match directory" in violation for violation in violations))

    def test_skill_cannot_copy_dynamic_policy_values(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skill = self.create_valid_registry(root)
            skill.write_text(skill.read_text(encoding="utf-8") + "runtime-mode\n", encoding="utf-8")
            violations = check_repository.tooling_governance_violations(root)
            self.assertTrue(any("copies dynamic policy value" in violation for violation in violations))

    def test_obsolete_skill_path_is_rejected_in_governed_docs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            (root / "WORKSPACE.md").write_text("tools/skills/tsuyomi-android-review/SKILL.md", encoding="utf-8")
            violations = check_repository.tooling_governance_violations(root)
            self.assertTrue(any("obsolete Skill path" in violation for violation in violations))

    def test_live_repository_tooling_governance_passes(self) -> None:
        self.assertEqual([], check_repository.tooling_governance_violations())

    def test_valid_document_registry_passes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            self.assertEqual([], check_repository.documentation_governance_violations(root))

    def test_document_requires_all_responsibility_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            registry = root / "DOCUMENTATION.md"
            registry.write_text(
                registry.read_text(encoding="utf-8").replace(
                    "| `TOOLING.md` | purpose | trigger | method | scope | complete | active |",
                    "| `TOOLING.md` | purpose | trigger | method | scope | | active |",
                ),
                encoding="utf-8",
            )
            violations = check_repository.documentation_governance_violations(root)
            self.assertTrue(any("TOOLING.md must define all seven responsibility fields" in violation for violation in violations))

    def test_unregistered_document_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            (root / "NEW.md").write_text("# Unregistered\n", encoding="utf-8")
            violations = check_repository.documentation_governance_violations(root)
            self.assertTrue(any("NEW.md: document is not registered" in violation for violation in violations))

    def test_environment_agent_instructions_are_not_registered_documents(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            (root / "AGENTS.md").write_text("# Environment instructions\n", encoding="utf-8")
            self.assertEqual([], check_repository.documentation_governance_violations(root))

    def test_duplicate_document_responsibility_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_valid_registry(root)
            registry = root / "DOCUMENTATION.md"
            registry.write_text(registry.read_text(encoding="utf-8") + "\n| `TOOLING.md` |\n", encoding="utf-8")
            violations = check_repository.documentation_governance_violations(root)
            self.assertTrue(any("TOOLING.md: document has 2 responsibility rows" in violation for violation in violations))

    def test_live_repository_documentation_governance_passes(self) -> None:
        self.assertEqual([], check_repository.documentation_governance_violations())


if __name__ == "__main__":
    unittest.main()
