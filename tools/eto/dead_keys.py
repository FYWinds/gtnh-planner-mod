"""Remove gtnhplanner.ui.* lang keys that no code names any more.

    python tools/eto/dead_keys.py <key suffix or glob>... [--write]

Each argument is a key without the `gtnhplanner.ui.` prefix (`powerpicker.takes_prefix`) or a glob
(`librarytail.couldn_t_*`). A key is dead when no string literal under src/main/java is the key, or a start of it ending
in `.` or `_`, or an end of it starting with one (keys are also built from a prefix and a name: `"audit.reason_" + x`
keeps every `audit.reason_*` key alive; such keys are reported as kept). Without --write it only reports; with it the
dead keys' lines are removed from every lang file.
"""
import fnmatch
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LANG = ROOT / "src/main/resources/assets/gtnhplanner/lang"
PREFIX = "gtnhplanner.ui."


def main() -> int:
    args = [a for a in sys.argv[1:] if a != "--write"]
    write = "--write" in sys.argv[1:]
    literals = set()
    for java in (ROOT / "src/main/java").rglob("*.java"):
        literals.update(re.findall(r'"((?:[^"\\\n]|\\.)*)"', java.read_text(encoding="utf-8")))
    keys = [
        line.split("=", 1)[0][len(PREFIX) :]
        for line in (LANG / "en_US.lang").read_text(encoding="utf-8").splitlines()
        if line.startswith(PREFIX) and "=" in line
    ]
    dead = []
    for key in keys:
        if not any(fnmatch.fnmatchcase(key, a) for a in args):
            continue
        used = key in literals or any(
            len(lit) > 3 and (lit[-1] in "._" and key.startswith(lit) or lit[0] in "._" and key.endswith(lit)) for lit in literals
        )
        print(("kept " if used else "dead ") + key)
        if not used:
            dead.append(PREFIX + key)
    if write and dead:
        for lang in LANG.glob("*.lang"):
            lines = lang.read_text(encoding="utf-8").split("\n")
            kept = [l for l in lines if l.split("=", 1)[0] not in dead]
            lang.write_text("\n".join(kept), encoding="utf-8", newline="\n")
            print(f"{lang.name}: removed {len(lines) - len(kept)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
