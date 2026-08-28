#!/usr/bin/env python3
"""Replace the 14 copy-pasted GlState records with the shared GlStateGuard.

Mechanical, and refuses rather than guesses. Three things must hold in every file or it
stops without writing anything:

  * exactly one `private record GlState` block, ending at the first line that is `    }`
    at record-body indentation
  * every `GlState` mention outside that block is either `GlState.capture()` or the
    declared type of the local
  * the file still contains no `GlState` after rewriting

That last check matters more than it looks. An earlier sed-derived edit in this project
renamed a class but left `ModEntities.MAGNETAR` untouched because the constant was
SCREAMING_CASE, which compiled cleanly and spawned the wrong entity. A rename that
compiles is not a rename that worked.
"""
import re
import sys
from pathlib import Path

ROOT = Path("src/main/java/com/gang/lightpollution/client/renderer")
RECORD_START = re.compile(r"^    private record GlState\b")

def strip_record(lines, path):
    starts = [i for i, l in enumerate(lines) if RECORD_START.match(l)]
    if len(starts) != 1:
        return None, f"{path.name}: expected 1 GlState record, found {len(starts)}"
    start = starts[0]
    end = None
    for i in range(start + 1, len(lines)):
        if lines[i].rstrip("\n") == "    }":
            end = i
            break
    if end is None:
        return None, f"{path.name}: GlState record has no closing brace at record indent"
    # Also drop a single blank line left behind above the removed block.
    cut_from = start
    if cut_from > 0 and lines[cut_from - 1].strip() == "":
        cut_from -= 1
    return lines[:cut_from] + lines[end + 1:], None

def main():
    targets = sorted(p for p in ROOT.glob("*.java")
                     if any(RECORD_START.match(l) for l in p.read_text(encoding="utf-8").splitlines(True)))
    if not targets:
        sys.exit("no files with a GlState record found — wrong directory?")

    planned = {}
    for path in targets:
        lines = path.read_text(encoding="utf-8").splitlines(True)
        trimmed, err = strip_record(lines, path)
        if err:
            sys.exit("REFUSING: " + err)
        body = "".join(trimmed)
        # The two real uses: the local's declared type and the factory call.
        body = body.replace("GlState state = GlState.capture();",
                            "GlStateGuard state = GlStateGuard.capture();")
        leftover = [m for m in re.findall(r"\bGlState\b", body)]
        if leftover:
            sys.exit(f"REFUSING: {path.name} still mentions GlState {len(leftover)}x "
                     f"after rewrite — unhandled usage, not touching anything")
        planned[path] = body

    for path, body in planned.items():
        path.write_text(body, encoding="utf-8")
    print(f"rewrote {len(planned)} renderers:")
    for path in planned:
        print("  " + path.name)

if __name__ == "__main__":
    main()
