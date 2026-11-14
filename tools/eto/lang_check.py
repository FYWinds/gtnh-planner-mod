"""Check a translated lang file against the English one, or find code keys the English file lacks.

    python tools/eto/lang_check.py                          # en_US.lang against zh_CN.lang in the mod's lang folder
    python tools/eto/lang_check.py BASE.lang OTHER.lang     # any two lang files: BASE is the reference
    python tools/eto/lang_check.py --code [BASE.lang]       # literal UiText.tr("...") keys under src/main/java not in BASE
Reports keys missing from or extra in OTHER, keys out of BASE's order, values whose format specifiers differ from
BASE's (count, kind, or which argument each one takes: `%2$s ... %1$s` may reorder arguments, plain `%s` may not),
lines that are not `key=value`, duplicate keys, a byte order mark and CR line ends.
Lines are split the way Minecraft does (at the first `=`, nothing trimmed); a key with blanks in it is printed as a
warning, since Minecraft keeps the blank in the key. Exits 1 when a problem (not a warning) is reported.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LANG_DIR = ROOT / "src/main/resources/assets/gtnhplanner/lang"
JAVA_DIR = ROOT / "src/main/java"

# java.util.Formatter: %[argument_index$][flags][width][.precision]conversion, plus %% and %n.
SPEC = re.compile(r"%(\d+\$|<)?([-#+ 0,(]*\d*(?:\.\d+)?(?:[tT][a-zA-Z]|[a-zA-Z%]))")
LITERAL = r'"((?:[^"\\]|\\.)*)"'
# UiText.tr("key" ...) and UiText.tr(condition ? "key" : "key"); in a class with a private `tr(String key)` that
# returns UiText.tr(key), its bare tr("key") calls as well.
UI_CALL = r"UiText\s*\.\s*tr\s*\(\s*"
LOCAL_CALL = r"(?<![\w.])tr\s*\(\s*"
LOCAL_WRAPPER = re.compile(r"String\s+tr\s*\(\s*(?:final\s+)?String\s+(\w+)\s*\)\s*\{\s*return\s+UiText\s*\.\s*tr\s*\(\s*\1\s*\)")
KEY_ARG = LITERAL + r"\s*[,)]"
CHOICE_ARG = r"[^;\"()]*(?:\([^;\"()]*\)[^;\"()]*)*\?\s*" + LITERAL + r"\s*:\s*" + LITERAL + r"\s*\)"
TR_CALL = re.compile(UI_CALL + KEY_ARG)
TR_CHOICE = re.compile(UI_CALL + CHOICE_ARG)
LOCAL_TR_CALL = re.compile(LOCAL_CALL + KEY_ARG)
LOCAL_TR_CHOICE = re.compile(LOCAL_CALL + CHOICE_ARG)
UI_PREFIX = "gtnhplanner.ui."


class LangFile:
    """A lang file read as ordered key/value pairs, with the problems and warnings found in its raw lines."""

    def __init__(self, path: Path):
        self.path = path
        self.entries: list[tuple[str, str]] = []
        self.problems: list[str] = []
        self.warnings: list[str] = []
        raw = path.read_bytes()
        if raw.startswith(b"\xef\xbb\xbf"):
            self.problems.append("starts with a UTF-8 byte order mark")
            raw = raw[3:]
        if b"\r" in raw:
            self.problems.append("has CR line ends (expected LF only)")
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as e:
            self.problems.append(f"is not valid UTF-8: {e}")
            text = raw.decode("utf-8", errors="replace")
        seen: dict[str, int] = {}
        for number, line in enumerate(text.replace("\r", "").split("\n"), 1):
            if not line or line.startswith("#"):
                continue
            key, sep, value = line.partition("=")
            if not sep or not key:
                self.problems.append(f"line {number}: not key=value: {line!r}")
                continue
            if key != key.strip() or " " in key:
                self.warnings.append(f"line {number}: key has blanks (Minecraft keeps them): {key!r}")
            if key in seen:
                self.problems.append(f"line {number}: duplicate key {key} (first on line {seen[key]})")
                continue
            seen[key] = number
            self.entries.append((key, value))

    def values(self) -> dict[str, str]:
        return dict(self.entries)


def specifiers(value: str) -> list[tuple[int, str]]:
    """Each argument-taking specifier as (argument number, the rest), sorted; %% and %n take none."""
    taken: list[tuple[int, str]] = []
    ordinary = 0
    last = 0
    for index, rest in SPEC.findall(value):
        if rest in ("%", "n"):
            continue
        if index == "<":
            argument = last
        elif index:
            argument = int(index[:-1])
        else:
            ordinary += 1
            argument = ordinary
        last = argument
        taken.append((argument, rest))
    return sorted(taken)


def literal_specifiers(value: str) -> list[str]:
    """The %% and %n escapes, which must stay in the text."""
    return sorted(rest for _, rest in SPEC.findall(value) if rest in ("%", "n"))


def compare(base: LangFile, other: LangFile) -> list[str]:
    problems = [f"{base.path.name}: {p}" for p in base.problems]
    problems += [f"{other.path.name}: {p}" for p in other.problems]
    base_values = base.values()
    other_values = other.values()
    for key in base_values:
        if key not in other_values:
            problems.append(f"missing in {other.path.name}: {key}")
    for key in other_values:
        # Data text has its English in the code, so only translations carry gtnhplanner.data.* lines.
        if key not in base_values and not key.startswith("gtnhplanner.data."):
            problems.append(f"extra in {other.path.name}: {key}")
    shared_base = [k for k, _ in base.entries if k in other_values]
    shared_other = [k for k, _ in other.entries if k in base_values]
    for index, (want, got) in enumerate(zip(shared_base, shared_other)):
        if want != got:
            problems.append(f"order differs from {base.path.name} at shared key {index + 1}: expected {want}, found {got}")
            break
    for key in shared_base:
        base_value = base_values[key]
        other_value = other_values[key]
        if specifiers(base_value) != specifiers(other_value) or literal_specifiers(base_value) != literal_specifiers(other_value):
            want = [m.group(0) for m in SPEC.finditer(base_value)]
            got = [m.group(0) for m in SPEC.finditer(other_value)]
            problems.append(f"format specifiers differ in {key}: {want} vs {got}")
    return problems


def code_keys() -> dict[str, list[str]]:
    """Literal UiText.tr keys under src/main/java (and of local tr wrappers), each with the files that use it."""
    found: dict[str, list[str]] = {}
    for path in sorted(JAVA_DIR.rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        patterns = [(TR_CALL, TR_CHOICE)]
        if LOCAL_WRAPPER.search(source):
            patterns.append((LOCAL_TR_CALL, LOCAL_TR_CHOICE))
        keys: list[str] = []
        for call, choice in patterns:
            keys += [m.group(1) for m in call.finditer(source)]
            keys += [k for m in choice.finditer(source) for k in m.groups()]
        for key in keys:
            found.setdefault(key, []).append(path.relative_to(ROOT).as_posix())
    return found


def check_code(base: LangFile) -> list[str]:
    problems = [f"{base.path.name}: {p}" for p in base.problems]
    defined = base.values()
    for key, files in sorted(code_keys().items()):
        if UI_PREFIX + key not in defined:
            problems.append(f"used in code, missing in {base.path.name}: {UI_PREFIX}{key} ({', '.join(sorted(set(files)))})")
    return problems


def main() -> int:
    args = sys.argv[1:]
    if args[:1] == ["--code"]:
        base = LangFile(Path(args[1]) if len(args) > 1 else LANG_DIR / "en_US.lang")
        files = [base]
        problems = check_code(base)
    else:
        if len(args) not in (0, 2):
            print(__doc__.strip(), file=sys.stderr)
            return 2
        base_path, other_path = (Path(a) for a in args) if args else (LANG_DIR / "en_US.lang", LANG_DIR / "zh_CN.lang")
        files = [LangFile(base_path), LangFile(other_path)]
        problems = compare(*files)
    for lang in files:
        for warning in lang.warnings:
            print(f"warning: {lang.path.name}: {warning}")
    for problem in problems:
        print(problem)
    print(f"{len(problems)} problem(s)", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
