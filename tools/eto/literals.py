"""English string literals in Java sources, for checking that every text a player sees has a translation.

    python tools/eto/literals.py src/main/java/com/gtnhplanner/machines            # file:line<TAB>literal
    python tools/eto/literals.py --missing parts/mach.zh_CN.lang DIR...           # only data texts with no zh line

A literal counts when it has a letter and is not inside a comment. --missing keeps the literals whose data key
(tools/eto/data_key.py) has no line in the given lang file(s), skipping ones that look like identifiers (no space and
no capital letter, or a key/path shape such as "machine:x", "gt.recipe.x", "a_b").
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from data_key import data_key  # noqa: E402

IDENT = re.compile(r"^[a-z0-9_.:/@%\-#+*\[\]{}()^$|\\?=,;<> ]*$")
KEYISH = re.compile(r"^[A-Za-z0-9_.:/@\-]+$")


def literals(path):
    """(line, text) for each string literal outside comments."""
    with open(path, encoding="utf-8") as f:
        src = f.read()
    out = []
    i, line, n = 0, 1, len(src)
    while i < n:
        c = src[i]
        if c == "\n":
            line += 1
            i += 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            line += src.count("\n", i, j)
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append((line, src[i + 3:j - 3]))
            line += src.count("\n", i, j)
            i = j
        elif c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            i = j + 1
        elif c == '"':
            j = i + 1
            buf = []
            while j < n and src[j] != '"':
                if src[j] == "\\":
                    nxt = src[j + 1]
                    if nxt == "u":
                        buf.append(chr(int(src[j + 2:j + 6], 16)))
                        j += 6
                        continue
                    buf.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\", "'": "'"}.get(nxt, nxt))
                    j += 2
                else:
                    buf.append(src[j])
                    j += 1
            out.append((line, "".join(buf)))
            i = j + 1
        else:
            i += 1
    return out


def java_files(paths):
    for p in paths:
        if os.path.isfile(p):
            yield p
            continue
        for root, _, files in os.walk(p):
            for name in sorted(files):
                if name.endswith(".java"):
                    yield os.path.join(root, name)


def lang_keys(files):
    keys = set()
    for f in files:
        with open(f, encoding="utf-8") as h:
            for raw in h:
                if "=" in raw and not raw.startswith("#"):
                    keys.add(raw.split("=", 1)[0].strip())
    return keys


def main():
    args = sys.argv[1:]
    missing = None
    if args[:1] == ["--missing"]:
        missing = lang_keys(args[1].split(","))
        args = args[2:]
    for path in java_files(args):
        for line, text in literals(path):
            if not re.search(r"[A-Za-z]", text):
                continue
            if missing is not None:
                if IDENT.match(text) or (KEYISH.match(text) and not re.search(r"[A-Z]", text)):
                    continue
                if data_key(text) in missing:
                    continue
            print(f"{path.replace(os.sep, '/')}:{line}\t{text}")


if __name__ == "__main__":
    main()
