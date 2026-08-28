"""Cross-checks that every damage fraction in the entity classes actually appears, as a
percentage, in the en_us description or info line for that spell. Catches the failure mode
where a rebalance lands in the code but the tooltip keeps quoting the old number.

Some tooltips legitimately quote an aggregate instead of the per-application figure --
Constellation and Stellar Convergence give per-second for damage applied twice a second,
and Chromatic Accretion gives the total of its four pulses -- so a small set of multiples
counts as a match.
"""
import glob
import io
import json
import os
import re

CODE = 'src/main/java/com/gang/lightpollution/entity/'
LANG = ('src/main/resources/assets/irons_spellbooks_light_pollution/lang/en_us.json')
PREFIX = 'spell.irons_spellbooks_light_pollution.'

# Modifiers and caps rather than damage figures in their own right.
SKIP = {'BLAST_BONUS_PER_LIGHT', 'STORED_DAMAGE_SHARE', 'STORED_DAMAGE_CAP_FRACTION',
        'PROJECTILE_DAMAGE_FRACTION', 'SWALLOWED_DAMAGE_STEP'}

# Multipliers a tooltip may legitimately quote instead of the raw per-application figure.
# 2 covers the two-a-second ticks that Constellation and Stellar Convergence render per
# second; 4 covers Chromatic Accretion, whose text gives the total of its four pulses.
FACTORS = (1, 2, 4)


def percent(value):
    return f'{round(value * 100, 4):g}%'


def snake(name):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def main():
    lang = json.load(io.open(LANG, encoding='utf-8'))
    missing = []
    checked = 0

    for path in sorted(glob.glob(CODE + '*Entity.java')):
        source = io.open(path, encoding='utf-8').read()
        name = os.path.basename(path).replace('Entity.java', '')
        key = PREFIX + snake(name)
        blob = lang.get(key + '.description', '') + ' ' + lang.get(key + '.info', '')
        if not blob.strip():
            continue

        for match in re.finditer(r'(\w+_FRACTION)\s*=\s*(0\.\d+)F', source):
            const, value = match.group(1), float(match.group(2))
            if const in SKIP:
                continue
            checked += 1
            if not any(percent(value * f) in blob for f in FACTORS):
                missing.append(f'{name}.{const} = {percent(value)}')

    print(f'checked {checked} constants')
    if missing:
        print('NOT FOUND in en_us text:')
        for m in missing:
            print('  ', m)
    else:
        print('every damage fraction is quoted in en_us')


main()
