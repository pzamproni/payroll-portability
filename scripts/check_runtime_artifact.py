#!/usr/bin/env python3
"""Prevent accidental test libraries in the executable production JAR."""
from pathlib import Path
from zipfile import ZipFile
import json

root = Path(__file__).resolve().parents[1]
jars = list((root / "backend/target").glob("payroll-portability-*.jar"))
assert len(jars) == 1, "Run Maven verify first; expected one executable JAR"
with ZipFile(jars[0]) as artifact:
    libraries = [Path(name).name for name in artifact.namelist() if name.startswith("BOOT-INF/lib/") and name.endswith(".jar")]
forbidden = ("junit-", "junit-platform-", "mockito-", "assertj-", "archunit-", "spring-test-", "spring-boot-test", "h2-", "hamcrest-", "jacoco-")
leaked = [name for name in libraries if name.startswith(forbidden)]
assert libraries, "Executable JAR has no runtime libraries"
assert not leaked, f"Test dependencies leaked into production: {leaked}"
result = {"runtimeJars": len(libraries), "testFrameworkJars": leaked}
(root / "reports").mkdir(exist_ok=True)
(root / "reports/runtime-artifact.json").write_text(json.dumps(result, indent=2) + "\n")
print(f"PASS: {len(libraries)} runtime JARs; zero test libraries in production.")
