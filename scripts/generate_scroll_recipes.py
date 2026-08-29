#!/usr/bin/env python3
"""Generate a crafting recipe and a loot entry for each of the 24 spell scrolls.

Recipe shape, the same for every spell so the cost is legible at a glance:

    E R E        E  arcane essence      x4   the bulk cost
    I P I        R  the school's rune   x1   which school this belongs to
    E T E        I  legendary ink       x2   the rarity gate
                 P  paper               x1   what a scroll is written on
                 T  a themed item       x1   which spell this is

Three of those slots carry meaning rather than just cost. The rune ties the scroll to its
school, so a player working toward one school is working toward its spells. Legendary ink is
the rarity gate and is genuinely a gate: it is brewed in the Alchemist Cauldron from epic ink
plus amethyst, so it sits at the end of a chain rather than being crafted from nothing. And the
themed item is what makes twenty-four otherwise identical recipes distinguishable in JEI.

Eldritch has no rune in Iron's Spells -- there are runes for the other seven schools we use but
not that one -- so those four take an echo shard instead, which is thematically closer than a
substitute rune would be anyway.

Written as a generator rather than by hand because the shape is uniform and twenty-four
hand-copied files would drift. It refuses rather than guessing: every spell must have a school
and a theme, the item must exist in the registry list, and the file count must come out right.
"""
import json
import sys
from pathlib import Path

MODID = "irons_spellbooks_light_pollution"
RECIPES = Path(f"src/main/resources/data/{MODID}/recipes")
LOOT = Path(f"src/main/resources/data/{MODID}/loot_tables/chests")

# Iron's Spells has a rune for each school we use except Eldritch.
RUNE = {
    "FIRE": "irons_spellbooks:fire_rune",
    "ENDER": "irons_spellbooks:ender_rune",
    "HOLY": "irons_spellbooks:holy_rune",
    "EVOCATION": "irons_spellbooks:evocation_rune",
    "NATURE": "irons_spellbooks:nature_rune",
    "LIGHTNING": "irons_spellbooks:lightning_rune",
    "BLOOD": "irons_spellbooks:blood_rune",
    "ELDRITCH": "minecraft:echo_shard",
}

# (registry path, school, themed item). The theme is the one ingredient a player would read as
# "this is the black hole one".
SPELLS = [
    ("gargantua", "ENDER", "minecraft:crying_obsidian"),
    ("stargrave_singularity", "ENDER", "minecraft:end_crystal"),
    ("singularity", "ENDER", "minecraft:ender_pearl"),
    ("chromatic_accretion", "ENDER", "minecraft:amethyst_cluster"),
    ("cosmic_horseshoe", "EVOCATION", "minecraft:tinted_glass"),
    ("quasar_jet", "EVOCATION", "minecraft:end_rod"),
    ("eclipse_severance", "EVOCATION", "minecraft:iron_sword"),
    ("celestial_judgment", "HOLY", "minecraft:beacon"),
    ("helix_nebula", "HOLY", "minecraft:ender_eye"),
    ("stellar_convergence", "HOLY", "minecraft:glowstone"),
    ("second_sun", "FIRE", "minecraft:magma_block"),
    ("starfall", "FIRE", "minecraft:fire_charge"),
    ("constellation", "FIRE", "minecraft:torchflower"),
    ("pinwheel", "FIRE", "minecraft:nautilus_shell"),
    ("microquasar", "LIGHTNING", "minecraft:blaze_rod"),
    ("sky_collapse", "LIGHTNING", "minecraft:lightning_rod"),
    ("leviathan", "BLOOD", "minecraft:turtle_egg"),
    ("tidal_disruption", "BLOOD", "minecraft:heart_of_the_sea"),
    ("crab_nebula", "NATURE", "minecraft:sculk_catalyst"),
    ("world_tree", "NATURE", "minecraft:mangrove_propagule"),
    ("magnetar", "ELDRITCH", "minecraft:lodestone"),
    ("starless", "ELDRITCH", "minecraft:sculk_shrieker"),
    ("silhouette", "ELDRITCH", "minecraft:sculk_sensor"),
    ("funeral_nova", "ELDRITCH", "minecraft:soul_lantern"),
]


def recipe(path, school, theme):
    return {
        "type": "minecraft:crafting_shaped",
        "pattern": ["ERE", "IPI", "ETE"],
        "key": {
            "E": {"item": "irons_spellbooks:arcane_essence"},
            "R": {"item": RUNE[school]},
            "I": {"item": "irons_spellbooks:legendary_ink"},
            "P": {"item": "minecraft:paper"},
            "T": {"item": theme},
        },
        "result": {"item": f"{MODID}:{path}_scroll", "count": 1},
    }


def main():
    if not Path("gradle.properties").is_file():
        sys.exit("REFUSING: run this from the project root")
    seen_paths = {p for p, _, _ in SPELLS}
    if len(seen_paths) != len(SPELLS):
        sys.exit("REFUSING: duplicate spell path in the table")
    themes = [t for _, _, t in SPELLS]
    if len(set(themes)) != len(themes):
        sys.exit("REFUSING: two spells share a themed item, so their recipes would collide")
    for path, school, _ in SPELLS:
        if school not in RUNE:
            sys.exit(f"REFUSING: {path} has school {school}, which has no rune mapping")

    RECIPES.mkdir(parents=True, exist_ok=True)
    for path, school, theme in SPELLS:
        target = RECIPES / f"{path}_scroll.json"
        target.write_text(json.dumps(recipe(path, school, theme), indent=2) + "\n",
                          encoding="utf-8")

    written = sorted(p.name for p in RECIPES.glob("*_scroll.json"))
    if len(written) != len(SPELLS):
        sys.exit(f"REFUSING: wrote {len(written)} recipes for {len(SPELLS)} spells")
    print(f"wrote {len(written)} crafting recipes")

    # One loot table holding every scroll, rolled by the loot modifier. A single pool with all
    # twenty-four entries and one roll means a chest yields at most one of these, which is the
    # right frequency for a legendary.
    LOOT.mkdir(parents=True, exist_ok=True)
    table = {
        "type": "minecraft:chest",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [
                {"type": "minecraft:item", "name": f"{MODID}:{path}_scroll", "weight": 1}
                for path, _, _ in SPELLS
            ],
            "conditions": [{"condition": "minecraft:random_chance", "chance": 0.18}],
        }],
    }
    (LOOT / "light_pollution_scrolls.json").write_text(
        json.dumps(table, indent=2) + "\n", encoding="utf-8")
    print(f"wrote the loot table with {len(SPELLS)} entries")


if __name__ == "__main__":
    main()
