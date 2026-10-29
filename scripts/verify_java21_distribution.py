#!/usr/bin/env python3
"""Verify the *actual* Supreme distributable: Java 21 ceiling and canonical identity."""
import argparse
import hashlib
from pathlib import Path
import re
import struct
import sys
import zipfile

MAIN = "com/github/relativobr/supreme/Supreme.class"
FORBIDDEN = (
    "org/junit/", "org/mockito/", "org/mockbukkit/",
    "io/github/thebusybiscuit/slimefun4/",
    "com/github/slimefun/",
)


def check_jar(path: Path, version: str) -> dict:
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)*", version):
        raise ValueError("Version must be a numeric release identity")
    expected_name = f"SF_Supreme{version}.jar"
    if path.name != expected_name or not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Missing or wrong Supreme canonical JAR: {expected_name}")
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate archive entry")
        if jar.testzip() is not None:
            raise ValueError("Archive has corrupt member bytes")
        if "plugin.yml" not in names or MAIN not in names:
            raise ValueError("Missing canonical plugin descriptor or main class")
        descriptor = jar.read("plugin.yml").decode("utf-8")
        def exact(field: str, value: str) -> None:
            match = re.search(rf"^{re.escape(field)}:[ \t]*[\x27\x22]?([^\s\x27\x22#]+)", descriptor, re.M)
            if not match or match.group(1) != value:
                raise ValueError(f"Packaged {field} does not match {value}")
        exact("name", "Supreme")
        exact("version", version)
        exact("main", "com.github.relativobr.supreme.Supreme")
        count = 0
        ceiling = 0
        for name in names:
            if name.startswith(FORBIDDEN):
                raise ValueError(f"Bundled core/test library is forbidden: {name}")
            if not name.endswith(".class"):
                continue
            data = jar.read(name)
            if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
                raise ValueError(f"Invalid Java class header: {name}")
            major = struct.unpack_from(">H", data, 6)[0]
            if major < 45 or major > 65:
                raise ValueError(f"Non-Java-21 bytecode (major {major}) in {name}")
            count += 1
            ceiling = max(ceiling, major)
        if not count:
            raise ValueError("No distributable classes found")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return {"name": expected_name, "classes": count, "max_major": ceiling, "sha256": digest}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("version")
    args = parser.parse_args()
    try:
        report = check_jar(args.jar, args.version)
    except (ValueError, OSError, zipfile.BadZipFile, UnicodeError) as exc:
        print(f"Supreme Java 21 distribution check: FAIL: {exc}", file=sys.stderr)
        return 1
    print(f"Supreme Java 21 distribution check: PASS: {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
