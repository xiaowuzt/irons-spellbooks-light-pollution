"""Checks that every spell class constructs the entity type that matches its own name.

This exists because the same mistake happened three times: each new spell was derived from
the previous one, and while the class names were substituted, the ModEntities constant is
SCREAMING_CASE and survived every rename. The result compiles cleanly and spawns the wrong
entity — a defect no compiler and no type checker can catch, because both entity types are
valid arguments to a constructor that takes EntityType<? extends X> via an unchecked cast
path. Only a name-consistency check finds it.
"""
import glob
import io
import os
import re
import sys


def expected_constant(class_name):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', class_name).upper()


def main():
    bad = []
    checked = 0
    for path in sorted(glob.glob('src/main/java/com/gang/lightpollution/spell/*Spell.java')):
        name = os.path.basename(path).replace('Spell.java', '')
        source = io.open(path, encoding='utf-8').read()
        used = set(re.findall(r'ModEntities\.([A-Z_]+)', source))
        if not used:
            continue
        checked += 1
        want = expected_constant(name)
        if used != {want}:
            bad.append(f'{name}Spell uses ModEntities.{sorted(used)} but should use {want}')

    print(f'checked {checked} spell classes')
    if bad:
        print('MISMATCHED ENTITY REFERENCES:')
        for b in bad:
            print('  ', b)
        return 1
    print('every spell constructs its own entity type')
    return 0


sys.exit(main())
