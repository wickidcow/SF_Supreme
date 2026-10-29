#!/usr/bin/env python3
"""Fail closed when the unpublished Supreme 1.0.21 source/JAR identity drifts."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.0.21"
JAR = f"SF_Supreme{VERSION}.jar"


class SupremeVersionCandidateContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
        cls.workflow = (ROOT / ".github/workflows/maven.yml").read_text(encoding="utf-8")
        cls.agents = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        cls.changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
        cls.notes = (ROOT / "docs/releases/1.0.21.md").read_text(encoding="utf-8")

    def test_distinct_version_matches_maven_workflow_and_maintainer_instructions(self):
        self.assertRegex(self.pom, r"<artifactId>Supreme</artifactId>\s*<version>1\.0\.21</version>")
        self.assertIn(f'VERSION: "{VERSION}"', self.workflow)
        self.assertIn(f'OUTPUT_NAME: "{JAR}"', self.workflow)
        self.assertIn(f"target/{JAR}", self.agents)
        self.assertIn("unpublished candidate", self.agents)

    def test_plugin_descriptor_still_receives_release_version(self):
        yml = (ROOT / "src/main/resources/plugin.yml").read_text(encoding="utf-8")
        self.assertIn("name: Supreme", yml)
        self.assertIn("version: ${project.version}", yml)
        self.assertIn("<finalName>SF_Supreme${project.version}</finalName>", self.pom)

    def test_release_is_new_and_prior_asset_is_intact_by_policy(self):
        self.assertIn("1.0.21 — unreleased validation candidate", self.changelog)
        self.assertIn("Not a published release", self.notes)
        self.assertIn("v1.0.20", self.notes)
        self.assertIn("if: startsWith(github.ref, 'refs/tags/v')", self.workflow)
        self.assertIn('if gh release view "$TAG"', self.workflow)
        self.assertNotIn("gh release upload", self.workflow)
        self.assertNotIn("--clobber", self.workflow)

    def test_candidate_contract_runs_before_build(self):
        self.assertIn("python3 -m unittest discover -s scripts -p 'test_release_candidate_identity.py' -v", self.workflow)
        self.assertIn("bash scripts/verify-supreme-invariants.sh .", self.workflow)
        self.assertIn("bash scripts/verify-doctor-migration.sh .", self.workflow)


if __name__ == "__main__":
    unittest.main()
