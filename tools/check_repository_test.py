# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from pathlib import Path
import unittest

from tools import check_repository


class RepositoryPolicyTest(unittest.TestCase):
    def test_component_scopes_select_only_the_requested_tree(self) -> None:
        paths = [
            Path("tsuyomi-android/app/src/main/kotlin/App.kt"),
            Path("tsuyomi-protocol/schemas/host-api.json"),
            Path("tsuyomi-extensions/src/wenku8.ts"),
            Path("README.md"),
        ]

        self.assertEqual([paths[0]], check_repository.paths_in_scope(paths, "android"))
        self.assertEqual([paths[1]], check_repository.paths_in_scope(paths, "protocol"))
        self.assertEqual([paths[2]], check_repository.paths_in_scope(paths, "extensions"))
        self.assertEqual(paths, check_repository.paths_in_scope(paths, "all"))

    def test_only_the_public_wenku8_hxp_fixture_is_allowed(self) -> None:
        self.assertFalse(
            check_repository.violates_policy(Path("tsuyomi-extensions/fixtures/wenku8/signed-fixture.hxp"))
        )
        self.assertTrue(check_repository.violates_policy(Path("tsuyomi-extensions/dist/private.hxp")))
        self.assertTrue(check_repository.violates_policy(Path("tsuyomi-extensions/fixtures/other/private.hxp")))

    def test_local_agent_and_sensitive_artifacts_are_rejected(self) -> None:
        rejected = [
            Path("tsuyomi-android/.local/report.json"),
            Path("tsuyomi-protocol/AGENTS.md"),
            Path("tsuyomi-extensions/.env.production"),
            Path("tsuyomi-android/release.jks"),
            Path("session.transcript.json"),
        ]

        self.assertTrue(all(check_repository.violates_policy(path) for path in rejected))
        self.assertFalse(check_repository.violates_policy(Path("tsuyomi-extensions/.env.example")))


if __name__ == "__main__":
    unittest.main()
