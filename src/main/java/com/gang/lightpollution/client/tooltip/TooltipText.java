package com.gang.lightpollution.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The last tooltip's lines as {@link Component}s, for the one style that needs them.
 *
 * <p>{@link net.minecraftforge.client.event.RenderTooltipEvent.Pre} hands over
 * {@code ClientTooltipComponent}s, which have already been flattened to
 * {@code FormattedCharSequence}. That is fine for every style that lets those components draw
 * themselves, but the pinwheel places each glyph individually and needs the styled text back.</p>
 *
 * <p>So the lines are kept when they are gathered. The ordering this relies on is not incidental:
 * vanilla gathers a tooltip's text and only then renders it — {@code Screen.getTooltipFromItem}
 * completes before {@code renderTooltip} is called — so by the time the render event fires, the
 * gather event for that same stack has already been and gone.</p>
 *
 * <p>The stack is held for comparison rather than for reading. If it does not match, the cache is
 * from some other tooltip and the pinwheel draws nothing rather than drawing the wrong words.</p>
 */
public final class TooltipText {
    private static ItemStack owner = ItemStack.EMPTY;
    private static List<Component> lines = List.of();

    private TooltipText() {
    }

    /** Remember a gathered tooltip. Called from the gather handler. */
    public static void capture(ItemStack stack, List<Component> gathered) {
        owner = stack;
        lines = List.copyOf(gathered);
    }

    public static void clearCache() {
        owner = ItemStack.EMPTY;
        lines = List.of();
    }

    /**
     * The lines belonging to this stack, or empty if the cache is for something else.
     *
     * <p>Compared by identity. Two scrolls of the same spell are equal but have their own tooltips
     * once enchantments or durability differ, and the identity check is what keeps them apart.</p>
     */
    public static List<Component> forStack(ItemStack stack) {
        if (stack == null || stack != owner) {
            return List.of();
        }
        // Blank lines are spacers in a normal tooltip; as a pinwheel spoke each one would claim an
        // arm of the wheel and leave a gap, so they are dropped here rather than in the renderer.
        List<Component> kept = new ArrayList<>(lines.size());
        for (Component line : lines) {
            if (line != null && !line.getString().isBlank()) {
                kept.add(line);
            }
        }
        return kept;
    }
}
