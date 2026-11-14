"""Lang keys for data text, the same as UiText.dataKey in the mod.

    python tools/eto/data_key.py "Large Bronze Boiler" "Steam"      # prints key=English for each argument
    python tools/eto/data_key.py --file strings.txt                 # one English text per line

A zh_CN line is the printed key, an equals sign and the translation.
"""
import sys


def java_hash(text: str) -> int:
    """String.hashCode: over UTF-16 code units, wrapped to 32 bits."""
    h = 0
    data = text.encode("utf-16-be")
    for i in range(0, len(data), 2):
        h = (31 * h + ((data[i] << 8) | data[i + 1])) & 0xFFFFFFFF
    return h


def data_key(english: str) -> str:
    out = []
    gap = False
    for ch in english:
        c = ch.lower() if "A" <= ch <= "Z" else ch
        if "a" <= c <= "z" or "0" <= c <= "9":
            if gap and out:
                out.append("_")
            gap = False
            out.append(c)
        else:
            gap = True
    slug = "".join(out)
    if len(slug) > 48:
        slug = slug[:48] + "_" + format(java_hash(english), "08x")
    return "gtnhplanner.data." + slug


def main() -> int:
    args = sys.argv[1:]
    if args[:1] == ["--file"]:
        with open(args[1], encoding="utf-8") as f:
            texts = [line.rstrip("\n") for line in f if line.strip()]
    else:
        texts = args
    for text in texts:
        print(f"{data_key(text)}={text}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
