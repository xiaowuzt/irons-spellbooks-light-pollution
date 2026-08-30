package com.gang.lightpollution.text;

import net.minecraftforge.fml.ModList;

/**
 * Decides whether this mod's copy of the text effects should run at all.
 *
 * <p>The effects came from the author's standalone text effects mod and reach vanilla text through
 * mixins on {@code Font}, {@code ItemStack} and {@code MutableComponent}. If a player has both mods
 * installed, both sets of mixins load and both try to parse the same strings — control codes stripped
 * by one leave nothing for the other, styles stack twice, and an {@code @Inject} whose target has
 * already been reshaped fails outright. With {@code defaultRequire: 1} that failure is a startup
 * crash.</p>
 *
 * <p>So this copy stands down when the standalone one is present. That one wins because it is the
 * more complete implementation: it reaches ModernUI's own text engine and FTB Quests' quest data,
 * which this copy also ports but which the standalone version is maintained against.</p>
 *
 * <p>Checked once and cached: it is consulted from the parser's hot path, which runs per string per
 * frame, and {@link ModList} lookups are not free.</p>
 */
public final class DynamicTextPort {
    /**
     * Mod ids of the standalone version these effects came from.
     *
     * <p>Two, because it was renamed. Dropping the old id would mean a player still on the old
     * standalone version got both copies parsing, which is the crash this class exists to prevent —
     * and the symptom would look like the rename broke something unrelated.</p>
     */
    private static final String[] STANDALONE_MOD_IDS = {"luminotype", "dynamic_text_effects"};

    private static Boolean standDown;

    private DynamicTextPort() {
    }

    /** True when the standalone mod is installed and should handle the effects instead. */
    public static boolean standDown() {
        Boolean cached = standDown;
        if (cached != null) {
            return cached;
        }
        boolean present;
        try {
            ModList mods = ModList.get();
            if (mods == null) {
                return false;
            }
            present = false;
            for (String modId : STANDALONE_MOD_IDS) {
                if (mods.isLoaded(modId)) {
                    present = true;
                    break;
                }
            }
        } catch (RuntimeException | LinkageError probeFailed) {
            // Asked before the mod list exists. Not standing down is the safe answer: the worst
            // case is that both parse for a few frames during loading, whereas standing down by
            // mistake would silently disable the effects for the whole session.
            return false;
        }
        standDown = present;
        return present;
    }
}
