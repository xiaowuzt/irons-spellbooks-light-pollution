package com.gang.lightpollution.text;

import net.minecraftforge.fml.ModList;

/**
 * Decides whether this mod's copy of the text effects should run at all.
 *
 * <p>The effects came from the author's standalone Dynamic Text Effects mod and reach vanilla
 * text through mixins on {@code Font}, {@code ItemStack} and {@code MutableComponent}. If a
 * player has both mods installed, both sets of mixins load and both try to parse the same
 * strings — control codes stripped by one leave nothing for the other, styles stack twice, and
 * an {@code @Inject} whose target has already been reshaped fails outright. With
 * {@code defaultRequire: 1} that failure is a startup crash.</p>
 *
 * <p>So this copy stands down when the original is present. The original is the more complete
 * implementation — it keeps the FTB Quests and ModernUI compatibility this port left behind —
 * so it is the right one to win.</p>
 *
 * <p>Checked once and cached: it is consulted from the parser's hot path, which runs per string
 * per frame, and {@link ModList} lookups are not free.</p>
 */
public final class DynamicTextPort {
    /** Mod id of the standalone version these effects were ported from. */
    private static final String ORIGINAL_MOD_ID = "dynamic_text_effects";

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
            present = ModList.get() != null && ModList.get().isLoaded(ORIGINAL_MOD_ID);
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
