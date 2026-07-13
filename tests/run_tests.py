#!/usr/bin/env python3
"""
Golden-file regression tests for the zserio diagram extension.

Each scenario runs the bundled zserio.jar against the railway example schema
and compares the generated output byte-for-byte with the checked-in expected
files in tests/expected/<scenario>/. Generated XMI files are additionally
run through validate_xmi.py.

Usage:
    python3 tests/run_tests.py             # run all tests
    python3 tests/run_tests.py --update    # regenerate the expected files
    python3 tests/run_tests.py --jar path/to/zserio.jar

Requires distr/zserio.jar (build with: ant zserio_bundle.install).
"""

import argparse
import difflib
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
EXPECTED_ROOT = REPO_ROOT / "tests" / "expected"
SCHEMA_DIR = REPO_ROOT / "zs"
SCHEMA_FILE = "railway.zs"
VALIDATOR = REPO_ROOT / "validate_xmi.py"

# Scenario name -> extra CLI arguments (all run on the railway example schema).
SCENARIOS = {
    "default": [],
    "all-formats": [
        "-diagram-format", "all",
    ],
    "format-list": [
        "-diagram-format", "plantuml,xmi",
    ],
    "split-packages": [
        "-diagram-format", "plantuml", "-diagram-split-packages",
    ],
    "content-options": [
        "-diagram-format", "plantuml,mermaid", "-diagram-include-subtypes",
        "-diagram-include-constants", "-diagram-direction", "left-to-right", "-diagram-ortho",
    ],
    "xmi-ea-diagrams": [
        "-diagram-format", "xmi", "-diagram-xmi-diagrams", "Train,RailNetwork,*State",
    ],
    "xmi-depth-1": [
        "-diagram-format", "xmi", "-diagram-xmi-diagrams", "Train", "-diagram-xmi-depth", "1",
    ],
}

# CLI arguments that must make zserio fail with the given message and no output.
ERROR_SCENARIOS = {
    "unknown-format": (["-diagram-format", "bogus"], "Unknown diagram format"),
    "legacy-both": (["-diagram-format", "both"], "Unknown diagram format"),
}


def run_zserio(jar, extra_args, output_dir):
    cmd = [
        "java", "-jar", str(jar), "-src", str(SCHEMA_DIR), SCHEMA_FILE,
        "-diagram", "-diagram-output", str(output_dir),
    ] + extra_args
    return subprocess.run(cmd, capture_output=True, text=True)


def list_files(root):
    return sorted(p.relative_to(root) for p in root.rglob("*") if p.is_file())


def compare_trees(generated, expected):
    """Return a list of human-readable problems (empty = match)."""
    problems = []
    gen_files = list_files(generated)
    exp_files = list_files(expected) if expected.is_dir() else []

    for missing in set(exp_files) - set(gen_files):
        problems.append(f"missing output file: {missing}")
    for extra in set(gen_files) - set(exp_files):
        problems.append(f"unexpected output file: {extra}")

    for rel in set(gen_files) & set(exp_files):
        gen_text = (generated / rel).read_text()
        exp_text = (expected / rel).read_text()
        if gen_text != exp_text:
            diff = list(difflib.unified_diff(
                exp_text.splitlines(), gen_text.splitlines(),
                fromfile=f"expected/{rel}", tofile=f"generated/{rel}", lineterm=""))
            preview = "\n    ".join(diff[:20])
            problems.append(f"content mismatch in {rel}:\n    {preview}")
    return problems


def validate_xmi(directory):
    problems = []
    xmi_files = sorted(directory.rglob("*.xmi"))
    for xmi in xmi_files:
        result = subprocess.run(
            [sys.executable, str(VALIDATOR), str(xmi)], capture_output=True, text=True)
        if result.returncode != 0:
            problems.append(f"validate_xmi.py failed for {xmi.name}:\n{result.stdout[-2000:]}")
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", default=str(REPO_ROOT / "distr" / "zserio.jar"))
    parser.add_argument("--update", action="store_true",
                        help="regenerate tests/expected from current output")
    args = parser.parse_args()

    jar = Path(args.jar)
    if not jar.is_file():
        sys.exit(f"zserio.jar not found at {jar} — build it first: ant zserio_bundle.install")

    failures = []

    with tempfile.TemporaryDirectory(prefix="zserio-diagram-tests-") as tmp:
        tmp = Path(tmp)

        for name, extra_args in SCENARIOS.items():
            output_dir = tmp / name
            result = run_zserio(jar, extra_args, output_dir)
            if result.returncode != 0:
                failures.append(f"[{name}] zserio failed (exit {result.returncode}):\n"
                                f"{result.stdout[-2000:]}{result.stderr[-2000:]}")
                print(f"FAIL  {name} (zserio error)")
                continue

            problems = validate_xmi(output_dir)

            if args.update:
                expected_dir = EXPECTED_ROOT / name
                if expected_dir.exists():
                    shutil.rmtree(expected_dir)
                shutil.copytree(output_dir, expected_dir)
                status = "UPDATED" if not problems else "UPDATED (XMI validation failed!)"
            else:
                problems += compare_trees(output_dir, EXPECTED_ROOT / name)
                status = "ok" if not problems else "FAIL"

            if problems:
                failures.append(f"[{name}]\n" + "\n".join(f"  {p}" for p in problems))
            print(f"{status:7s} {name}")

        for name, (extra_args, expected_message) in ERROR_SCENARIOS.items():
            output_dir = tmp / f"error-{name}"
            result = run_zserio(jar, extra_args, output_dir)
            output = result.stdout + result.stderr
            problems = []
            if result.returncode == 0:
                problems.append("expected non-zero exit code")
            if expected_message not in output:
                problems.append(f"expected message '{expected_message}' not in output")
            if output_dir.exists() and list_files(output_dir):
                problems.append("error case must not generate output files")
            if problems:
                failures.append(f"[error:{name}]\n" + "\n".join(f"  {p}" for p in problems))
            print(f"{'ok' if not problems else 'FAIL':7s} error:{name}")

    if failures:
        print("\n" + "=" * 70)
        print(f"{len(failures)} test(s) failed:\n")
        print("\n\n".join(failures))
        sys.exit(1)

    print("\nAll tests passed." if not args.update else "\nExpected files regenerated.")


if __name__ == "__main__":
    main()
