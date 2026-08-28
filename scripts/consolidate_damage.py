#!/usr/bin/env python3
"""Replace the 20 identical private applyTrueDamage copies with SpellDamage.apply.

Two edits per file: delete the method, and rewrite each call to pass the anchor.
Every call site is inside an instance method of the anchor entity, so `this` is the
anchor — that is what makes the rewrite mechanical rather than a judgement call.

Refuses unless all of these hold, and writes nothing at all if any file fails:

  * the file's applyTrueDamage body is byte-identical to the canonical one
  * every remaining mention of applyTrueDamage is a call of the exact expected shape
  * no mention of applyTrueDamage survives the rewrite

StargraveSingularityEntity is skipped by the identity check on purpose: its version
takes a precomputed snapshot and carries extra boss handling, so it is a different
method that shares a name.

The last check is the important one. An earlier sed-derived edit in this project
renamed a class but left `ModEntities.MAGNETAR` alone because the constant was
SCREAMING_CASE — it compiled cleanly and spawned the wrong entity. Compiling is not
evidence that a rewrite worked.
"""
import re
import sys
from pathlib import Path

ROOT = Path("src/main/java/com/gang/lightpollution/entity")
CANONICAL = """    private static void applyTrueDamage(LivingEntity target, DamageSource source, float fraction) {
        float damage = Math.max(0.0F, target.getMaxHealth() * fraction);
        float desiredHealth = Math.max(0.0F, target.getHealth() - damage);

        target.invulnerableTime = 0;
        target.hurt(source, damage);
        target.invulnerableTime = 0;

        if (target.isDeadOrDying() || target.isRemoved()) {
            return;
        }

        target.setAbsorptionAmount(0.0F);
        float finalHealth = Math.min(target.getHealth(), desiredHealth);
        if (finalHealth <= 0.0F) {
            target.setHealth(0.0F);
            if (!target.isRemoved()) {
                target.die(source);
            }
        } else {
            target.setHealth(finalHealth);
        }
    }
"""
CALL = re.compile(r"\bapplyTrueDamage\(")


def normalise(text):
    return re.sub(r"\s+", "", text)


def find_method(text):
    """Span of the applyTrueDamage method, or None. Ends at the first `    }` line."""
    marker = "    private static void applyTrueDamage"
    if marker not in text:
        return None
    start = text.index(marker)
    lines = text[start:].splitlines(True)
    for offset, line in enumerate(lines):
        if offset > 0 and line.rstrip("\n") == "    }":
            return start, start + len("".join(lines[:offset + 1]))
    return None


def main():
    canonical = normalise(CANONICAL)
    planned = {}
    skipped = []
    for path in sorted(ROOT.glob("*.java")):
        text = path.read_text(encoding="utf-8")
        if "applyTrueDamage" not in text:
            continue
        span = find_method(text)
        # Whitespace-insensitive: nine of these wrap the signature across two lines but are
        # otherwise the same method. Comparing raw text would silently leave those behind.
        if span is None or normalise(text[span[0]:span[1]]) != canonical:
            skipped.append(path.name)
            continue

        body = text[:span[0]] + text[span[1]:]
        # Collapse the blank-line pair the removal leaves behind.
        body = body.replace("\n\n\n", "\n\n")
        body = CALL.sub("SpellDamage.apply(this, ", body)

        leftover = CALL.findall(body)
        if leftover:
            sys.exit(f"REFUSING: {path.name} still mentions applyTrueDamage "
                     f"{len(leftover)}x after rewrite")
        if "SpellDamage.apply(this, " not in body:
            sys.exit(f"REFUSING: {path.name} had the method but no call site — "
                     f"unexpected shape, not touching anything")
        planned[path] = body

    if not planned:
        sys.exit("nothing matched the canonical body — wrong directory or already done?")

    for path, body in planned.items():
        path.write_text(body, encoding="utf-8")
    print(f"rewrote {len(planned)} entities")
    for path in planned:
        print("  " + path.name)
    if skipped:
        print(f"left alone (body differs): {', '.join(skipped)}")

if __name__ == "__main__":
    main()
