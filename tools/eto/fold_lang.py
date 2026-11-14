"""Fold lang/parts/<name>.<lang>.lang into lang/<lang>.lang and delete the parts.

    python tools/eto/fold_lang.py [--check]

Work done in parallel writes its new lang lines to parts files so the real files do not conflict. Each part becomes a
section at the end of its language's file, under a `## <name>` line, in the part's own order. A key already in the
file, or in an earlier part, with the same value is dropped; with another value it is an error and nothing is written.
--check only reports.
"""
import sys
from pathlib import Path

LANG = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gtnhplanner/lang"


def entries(path: Path) -> list[tuple[str, str]]:
    out = []
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if "=" not in line:
            raise SystemExit(f"{path.name}:{n}: not key=value: {line!r}")
        key, value = line.split("=", 1)
        out.append((key, value))
    return out


def main() -> int:
    check = "--check" in sys.argv[1:]
    parts = sorted((LANG / "parts").glob("*.lang")) if (LANG / "parts").is_dir() else []
    problems = 0
    for lang in sorted({p.name.split(".")[-2] for p in parts}):
        target = LANG / f"{lang}.lang"
        known = dict(entries(target)) if target.exists() else {}
        text = target.read_text(encoding="utf-8") if target.exists() else ""
        added = 0
        for part in [p for p in parts if p.name.endswith(f".{lang}.lang")]:
            name = part.name[: -len(f".{lang}.lang")]
            lines = []
            for key, value in entries(part):
                if key in known:
                    if known[key] != value:
                        print(f"{part.name}: {key} is already {known[key]!r}, part says {value!r}")
                        problems += 1
                    continue
                known[key] = value
                lines.append(f"{key}={value}")
            if lines:
                text = text.rstrip("\n") + f"\n\n## {name}\n" + "\n".join(lines) + "\n"
                added += len(lines)
        print(f"{lang}: {added} lines from parts, {len(known)} keys in all")
        if not check and not problems:
            target.write_text(text, encoding="utf-8", newline="\n")
    if problems:
        print(f"{problems} conflicting keys; nothing written")
        return 1
    if not check:
        for part in parts:
            part.unlink()
        if parts:
            (LANG / "parts").rmdir()
    return 0


if __name__ == "__main__":
    sys.exit(main())
