#!/usr/bin/env python3
"""Check that the example programs match between the Android and iOS apps.

Example programs are duplicated in two hand-maintained files:

  * Android: shared/src/commonMain/kotlin/com/kaappi/studio/data/ExampleRepository.kt
  * iOS:     iosApp/KaappiStudio/Helpers/Examples.swift

AGENTS.md requires both copies to agree on id, title, description, category
and code for every example. This script extracts those fields from each file
at the source level (no code generation, no dependencies beyond the Python
standard library) and diffs them. It runs in Android CI on every push and
pull request.

The extraction models the runtime string of each language:

  * Kotlin: a raw string passed to ``.trimMargin()`` strips leading whitespace
    plus the ``|`` margin prefix from every line and drops blank first/last
    lines.
  * Swift: multiline literals drop the newline after the opening delimiter
    and strip the closing delimiter's indentation from every line.

Exit status is 0 when the two files are in sync, 1 otherwise.
"""

import difflib
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

KOTLIN_PATH = REPO_ROOT / "shared/src/commonMain/kotlin/com/kaappi/studio/data/ExampleRepository.kt"
SWIFT_PATH = REPO_ROOT / "iosApp/KaappiStudio/Helpers/Examples.swift"

# Kotlin ExampleCategory name -> canonical category label
KOTLIN_CATEGORIES = {
    "GETTING_STARTED": "Getting Started",
    "FUNCTIONS": "Functions",
    "DATA_STRUCTURES": "Data Structures",
    "CONTROL_FLOW": "Control Flow",
    "ADVANCED": "Advanced",
}

# Swift ExampleCategory case -> canonical category label
SWIFT_CATEGORIES = {
    "gettingStarted": "Getting Started",
    "functions": "Functions",
    "dataStructures": "Data Structures",
    "controlFlow": "Control Flow",
    "advanced": "Advanced",
}

# A Kotlin string literal: no escapes appear in these files, but tolerate the
# common ones so a future title/description with a quote keeps parsing.
KOTLIN_STRING = r'"(?P<%s>(?:[^"\\]|\\.)*)"'

KOTLIN_BLOCK = re.compile(
    r'Example\(\s*'
    r'id\s*=\s*' + KOTLIN_STRING % "id" + r'\s*,\s*'
    r'title\s*=\s*' + KOTLIN_STRING % "title" + r'\s*,\s*'
    r'description\s*=\s*' + KOTLIN_STRING % "description" + r'\s*,\s*'
    r'category\s*=\s*ExampleCategory\.([A-Z_]+)\s*,\s*'
    r'code\s*=\s*"""(?P<code>.*?)"""\s*\.trimMargin\(\s*\)',
    re.S,
)

SWIFT_BLOCK = re.compile(
    r'id:\s*"(?P<id>[^"]*)"\s*,\s*title:\s*"(?P<title>[^"]*)"\s*,\s*'
    r'description:\s*"(?P<description>[^"]*)"\s*,\s*'
    r'category:\s*\.([A-Za-z]+)\s*,\s*code:\s*"""(?P<code>.*?)"""',
    re.S,
)

KOTLIN_UNESCAPES = {r"\"": '"', r"\\": "\\", r"\n": "\n", r"\t": "\t", r"\$": "$"}


def unescape_kotlin(literal: str) -> str:
    out, i = [], 0
    while i < len(literal):
        ch = literal[i]
        if ch == "\\" and i + 1 < len(literal):
            out.append(KOTLIN_UNESCAPES.get(literal[i : i + 2], literal[i + 1]))
            i += 2
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def decode_kotlin_code(raw: str) -> str:
    """Model ``raw`` as the runtime value produced by ``.trimMargin()``."""
    lines = raw.split("\n")
    # trimMargin removes the first and the last lines when they are blank.
    if lines and not lines[0].strip():
        lines = lines[1:]
    if lines and not lines[-1].strip():
        lines = lines[:-1]
    decoded = []
    for line in lines:
        stripped = line.lstrip(" \t")
        if stripped.startswith("|"):
            decoded.append(stripped[1:])
        else:
            decoded.append(line)
    return "\n".join(decoded)


def decode_swift_code(raw: str) -> str:
    """Model ``raw`` as the runtime value of a Swift multiline string literal.

    ``raw`` starts just after the opening ``\"\"\"`` (its newline is part of
    the literal syntax) and ends just before the closing ``\"\"\"``, so the
    final split element is the closing delimiter's indentation.
    """
    lines = raw.split("\n")
    # The capture ends just before the closing delimiter, so the final split
    # element is that delimiter's indentation.
    closing_indent = lines[-1] if lines else ""
    if lines and not (closing_indent == "" or closing_indent.isspace()):
        raise ValueError("unexpected Swift multiline literal: closing delimiter is not on its own line")
    indent = len(closing_indent)
    body = lines[1:-1]
    decoded = []
    for line in body:
        if line == "":
            decoded.append(line)
        elif len(line) >= indent and line[:indent].isspace():
            decoded.append(line[indent:])
        else:
            raise ValueError(f"Swift line is less indented than the closing delimiter: {line!r}")
    return "\n".join(decoded)


def parse_kotlin(text: str) -> list[tuple]:
    examples = []
    for match in KOTLIN_BLOCK.finditer(text):
        category = KOTLIN_CATEGORIES.get(match.group(4))
        if category is None:
            raise ValueError(f"unknown Kotlin category: ExampleCategory.{match.group(4)}")
        examples.append(
            (
                unescape_kotlin(match.group(1)),
                unescape_kotlin(match.group(2)),
                unescape_kotlin(match.group(3)),
                category,
                decode_kotlin_code(match.group(5)),
            )
        )
    return examples


def parse_swift(text: str) -> list[tuple]:
    examples = []
    for match in SWIFT_BLOCK.finditer(text):
        category = SWIFT_CATEGORIES.get(match.group(4))
        if category is None:
            raise ValueError(f"unknown Swift category: .{match.group(4)}")
        examples.append(
            (
                match.group(1),
                match.group(2),
                match.group(3),
                category,
                decode_swift_code(match.group(5)),
            )
        )
    return examples


def report_diff(kotlin: list[tuple], swift: list[tuple]) -> list[str]:
    problems = []
    kotlin_by_id = {e[0]: e for e in kotlin}
    swift_by_id = {e[0]: e for e in swift}

    only_kotlin = [e[0] for e in kotlin if e[0] not in swift_by_id]
    only_swift = [e[0] for e in swift if e[0] not in kotlin_by_id]
    if only_kotlin:
        problems.append(f"missing on iOS (present in ExampleRepository.kt): {', '.join(only_kotlin)}")
    if only_swift:
        problems.append(f"missing on Android (present in Examples.swift): {', '.join(only_swift)}")

    for ex_id, kt in kotlin_by_id.items():
        if ex_id not in swift_by_id:
            continue
        sw = swift_by_id[ex_id]
        for index, field in enumerate(("title", "description", "category"), start=1):
            if kt[index] != sw[index]:
                problems.append(f"{ex_id}: {field} differs\n  Android: {kt[index]!r}\n  iOS:     {sw[index]!r}")
        if kt[4] != sw[4]:
            diff = "\n".join(
                difflib.unified_diff(
                    kt[4].splitlines(),
                    sw[4].splitlines(),
                    fromfile=f"ExampleRepository.kt:{ex_id}",
                    tofile=f"Examples.swift:{ex_id}",
                    lineterm="",
                )
            )
            problems.append(f"{ex_id}: code differs\n{diff}")

    kt_order = [e[0] for e in kotlin if e[0] in swift_by_id]
    sw_order = [e[0] for e in swift if e[0] in kotlin_by_id]
    if kt_order != sw_order:
        problems.append(f"ordering differs\n  Android: {kt_order}\n  iOS:     {sw_order}")

    return problems


def main() -> int:
    kotlin = parse_kotlin(KOTLIN_PATH.read_text(encoding="utf-8"))
    swift = parse_swift(SWIFT_PATH.read_text(encoding="utf-8"))
    if not kotlin:
        raise ValueError(f"no examples parsed from {KOTLIN_PATH}")
    if not swift:
        raise ValueError(f"no examples parsed from {SWIFT_PATH}")

    problems = report_diff(kotlin, swift)
    if problems:
        print(
            f"Example parity check FAILED: ExampleRepository.kt has {len(kotlin)} examples, "
            f"Examples.swift has {len(swift)}.\n"
        )
        for problem in problems:
            print(problem)
            print()
        print("Example programs are duplicated; update both copies with matching")
        print("id/title/description/category/code (see docs/development.md), then rerun:")
        print("  python3 scripts/check-examples-parity.py")
        return 1

    print(f"Example parity OK: {len(kotlin)} examples match between Android and iOS.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
