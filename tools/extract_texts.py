# Lists the texts still hard-coded in the client code (so they can be moved to the language files with gen_lang.py).
# Usage: python tools/extract_texts.py   -> prints "file<TAB>text"
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CLIENT = os.path.join(HERE, "..", "src", "main", "java", "io", "github", "romainmichaux", "fieldinvestigations", "client")
FILES = sorted(f for f in os.listdir(CLIENT) if f.endswith(".java"))
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
PHASES = {"SAMPLING", "TRACKING", "APPROACH", "FLUSHED", "SPOTTING"}


def is_human_text(t):
    if t in PHASES or len(t) == 0:
        return False
    if re.fullmatch(r"[a-z0-9_:./%-]*", t):          # ids, NBT keys, texture names
        return False
    if not re.search(r"[A-Za-zÀ-ÿ]{2,}", t):           # symbols, digits, single letters (A, T, C, G, N, 95°...)
        return False
    if t.startswith("cobblemon_field_investigations.") or t.startswith("§") and len(t) <= 3:
        return False
    return True


def texts(file):
    source = io.open(os.path.join(CLIENT, file), encoding="utf-8").read()
    found = []
    for line in source.split("\n"):
        if line.strip().startswith("//"):
            continue
        for m in LITERAL.finditer(line):
            before = line[:m.start()]
            # a literal after a // comment is not code
            if "//" in LITERAL.sub('""', before):
                continue
            if is_human_text(m.group(1)) and "T.f(" not in before[-5:] and "T.t(" not in before[-5:] and "T.c(" not in before[-5:]:
                found.append(m.group(1))
    return found


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    seen = set()
    for f in FILES:
        for t in texts(f):
            if t not in seen:
                seen.add(t)
                print(f + "\t" + t)
