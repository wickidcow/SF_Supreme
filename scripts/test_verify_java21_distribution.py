#!/usr/bin/env python3
"""Regression tests for canonical raw Supreme Java 21 artifacts."""
import tempfile
from pathlib import Path
import sys
import unittest
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from verify_java21_distribution import check_jar

VERSION = "1.0.21"
MAIN = "com/github/relativobr/supreme/Supreme.class"


def java_class(major=65, minor=0):
    return b"\xca\xfe\xba\xbe" + minor.to_bytes(2, "big") + major.to_bytes(2, "big") + b"\x00\x01"


class SupremeDistributionContractTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.jar = Path(self.tmp.name) / f"SF_Supreme{VERSION}.jar"
        self.descriptor = "name: Supreme\nversion: 1.0.21\nmain: com.github.relativobr.supreme.Supreme\n"
        self.class_data = java_class()
        self.extras = {}
        self.duplicate_main = False

    def prepare(self):
        with zipfile.ZipFile(self.jar, "w") as out:
            out.writestr("plugin.yml", self.descriptor)
            if self.class_data is not None:
                out.writestr(MAIN, self.class_data)
            for name, data in self.extras.items():
                out.writestr(name, data)
            if self.duplicate_main:
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    out.writestr(MAIN, java_class())

    def rejects(self):
        self.prepare()
        with self.assertRaises((ValueError, zipfile.BadZipFile, OSError)):
            check_jar(self.jar, VERSION)

    def test_matching_unshaded_java21_is_accepted(self):
        self.prepare()
        result = check_jar(self.jar, VERSION)
        self.assertEqual(1, result["classes"])
        self.assertEqual(65, result["max_major"])
        self.assertEqual(64, len(result["sha256"]))

    def test_java21_preview_class_is_refused(self):
        self.class_data = java_class(65, 65535)
        self.rejects()

    def test_earlier_java_preview_classes_are_refused(self):
        for major in (56, 61, 64):
            with self.subTest(major=major):
                self.class_data = java_class(major, 65535)
                self.rejects()

    def test_nonzero_modern_minor_is_refused(self):
        for major, minor in ((56, 1), (61, 42), (65, 65534)):
            with self.subTest(major=major, minor=minor):
                self.class_data = java_class(major, minor)
                self.rejects()

    def test_preview_hidden_in_another_class_is_refused(self):
        self.extras = {"com/github/relativobr/supreme/Preview.class": java_class(65, 65535)}
        self.rejects()

    def test_stable_java21_with_older_class_still_passes(self):
        self.extras = {"com/github/relativobr/supreme/Older.class": java_class(52)}
        self.prepare()
        result = check_jar(self.jar, VERSION)
        self.assertEqual(2, result["classes"])
        self.assertEqual(65, result["max_major"])

    def test_historical_pre_preview_minors_remain_accepted(self):
        for major, minor in ((45, 3), (52, 1), (55, 65535)):
            with self.subTest(major=major, minor=minor):
                self.class_data = java_class(major, minor)
                self.prepare()
                self.assertEqual(1, check_jar(self.jar, VERSION)["classes"])

    def test_java25_class_is_refused(self):
        self.class_data = java_class(69)
        self.rejects()

    def test_malformed_class_header_is_refused(self):
        self.class_data = b"wrong"
        self.rejects()

    def test_missing_main_class_is_refused(self):
        self.class_data = None
        self.rejects()

    def test_wrong_version_is_refused(self):
        self.descriptor = self.descriptor.replace("1.0.21", "1.0.20")
        self.rejects()

    def test_wrong_canonical_plugin_name_is_refused(self):
        self.descriptor = self.descriptor.replace("name: Supreme", "name: OtherPlugin")
        self.rejects()

    def test_embedded_slimefun_core_is_refused(self):
        self.extras = {"io/github/thebusybiscuit/slimefun4/core/Core.class": java_class()}
        self.rejects()

    def test_embedded_test_libraries_are_refused(self):
        for prefix in ("org/junit/", "org/mockito/", "org/mockbukkit/"):
            self.extras = {prefix + "Test.class": java_class()}
            self.rejects()

    def test_duplicate_zip_members_are_refused(self):
        self.duplicate_main = True
        self.rejects()

    def test_canonical_archive_filename_is_required(self):
        self.prepare()
        renamed = self.jar.with_name("SF_Supreme1.0.20.jar")
        self.jar.rename(renamed)
        with self.assertRaises(ValueError):
            check_jar(renamed, VERSION)


if __name__ == "__main__":
    unittest.main()
