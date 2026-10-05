package com.gang.lightpollution;

import java.util.HashMap;
import java.util.Map;

/**
 * One accent colour per spell, for everything that needs to be tinted per spell.
 *
 * <p>Lives in the common package rather than the client one because two sides need it now. Tooltips
 * pick their colours here, and so does the floating damage text — and that colour has to be chosen
 * on the server, since that is where damage is resolved and where the packet is filled in.</p>
 *
 * <p>Keyed on the registry path, which spells, entities and scroll items all share: the entity is
 * registered as {@code crab_nebula} and the item as {@code crab_nebula_scroll}, so stripping the
 * suffix is the only adjustment needed.</p>
 *
 * <p>Still expressed as palette letters rather than colours directly, because several spells were
 * deliberately grouped onto a shared palette and that grouping is what the letters record.</p>
 */
public final class SpellPalette {
    /** Which palette each spell uses. */
    private static final Map<String, String> PALETTES = new HashMap<>();

    static {
        PALETTES.put("celestial_judgment", "y");
        PALETTES.put("stargrave_singularity", "q");
        PALETTES.put("eclipse_severance", "u");
        PALETTES.put("funeral_nova", "t");
        PALETTES.put("chromatic_accretion", "p");
        PALETTES.put("starless", "g");
        PALETTES.put("constellation", "t");
        PALETTES.put("silhouette", "h");
        PALETTES.put("starfall", "u");
        PALETTES.put("sky_collapse", "g");
        PALETTES.put("stellar_convergence", "m");
        PALETTES.put("second_sun", "t");
        PALETTES.put("singularity", "q");
        PALETTES.put("leviathan", "v");
        PALETTES.put("world_tree", "v");
        PALETTES.put("gargantua", "q");
        PALETTES.put("event_horizon", "t");
        PALETTES.put("redshift_abyss", "t");
        PALETTES.put("schwarzschild_lens", "t");
        PALETTES.put("radiant_collapse", "t");
        PALETTES.put("stasis_singularity", "t");
        PALETTES.put("cosmic_horseshoe", "y");
        PALETTES.put("microquasar", "m");
        PALETTES.put("helix_nebula", "y");
        PALETTES.put("magnetar", "s");
        PALETTES.put("tidal_disruption", "u");
        PALETTES.put("quasar_jet", "s");
        PALETTES.put("pinwheel", "p");
        PALETTES.put("crab_nebula", "h");
    }

    /** The colour behind each palette letter. */
    private static final Map<String, Integer> COLOURS = new HashMap<>();

    static {
        COLOURS.put("y", 0x8FD8FF);
        COLOURS.put("q", 0xB77BFF);
        COLOURS.put("u", 0xFF6A6A);
        COLOURS.put("t", 0xFFAE5C);
        COLOURS.put("p", 0x9CE8FF);
        COLOURS.put("g", 0x9AA6C4);
        COLOURS.put("h", 0x7FE8E0);
        COLOURS.put("m", 0xC79BFF);
        COLOURS.put("s", 0x9CD4FF);
        COLOURS.put("v", 0xFFC98A);
    }

    /** Fallback for one of ours that is not in the table. */
    public static final int DEFAULT_ACCENT = 0xA8C0FF;

    private SpellPalette() {
    }

    /** The palette letter for a registry path, or null if it is not one of ours. */
    public static String paletteFor(String path) {
        return PALETTES.get(strip(path));
    }

    /** The accent for a registry path, falling back to {@link #DEFAULT_ACCENT}. */
    public static int accentFor(String path) {
        String palette = PALETTES.get(strip(path));
        return palette == null ? DEFAULT_ACCENT : COLOURS.getOrDefault(palette, DEFAULT_ACCENT);
    }

    /** The colour behind a palette letter. */
    public static int colourOf(String palette) {
        return COLOURS.getOrDefault(palette, DEFAULT_ACCENT);
    }

    /**
     * The accent belonging to a spell's anchor entity.
     *
     * <p>Here rather than in the callers because both things that need a colour on the server — the
     * damage numbers and the captions — have an anchor in hand and nothing else identifying.</p>
     */
    public static int accentFor(net.minecraft.world.entity.Entity anchor) {
        if (anchor == null) {
            return DEFAULT_ACCENT;
        }
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(anchor.getType());
        return id == null ? DEFAULT_ACCENT : accentFor(id.getPath());
    }

    private static String strip(String path) {
        return path != null && path.endsWith("_scroll")
                ? path.substring(0, path.length() - "_scroll".length())
                : path;
    }
}
