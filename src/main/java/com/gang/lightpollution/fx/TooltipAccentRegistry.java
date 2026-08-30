package com.gang.lightpollution.fx;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Items other mods have asked to be given a tooltip frame, and the accent colour to draw each in.
 *
 * <p>Two ways in, because they answer different questions. An item registration is for "this exact
 * item"; a tag registration is for "anything the pack marks as mine", which is what a modpack author
 * wants and what an item-by-item list cannot express.</p>
 *
 * <p>Items win over tags. A caller that registered both was specific about the item and general about
 * the tag, so the specific one is the one they meant.</p>
 *
 * <p>Concurrent because registration happens during mod setup, on whatever thread the caller is on,
 * while lookups happen on the render thread once a tooltip is up.</p>
 */
public final class TooltipAccentRegistry {
    private static final Map<Item, Integer> BY_ITEM = new ConcurrentHashMap<>();
    /**
     * Tag registrations, in order.
     *
     * <p>A list rather than a map: a stack can carry several tags, and iterating a small list in
     * registration order gives a caller a rule they can predict. A map keyed by tag would need the
     * stack's whole tag set looked up and would leave ties to hash order.</p>
     */
    private static final List<TagEntry> BY_TAG = new CopyOnWriteArrayList<>();

    private record TagEntry(TagKey<Item> tag, int accent) {
    }

    private TooltipAccentRegistry() {
    }

    /** Register one item. Replaces any previous registration for it. */
    public static void register(Item item, int accentRgb) {
        if (item != null) {
            BY_ITEM.put(item, accentRgb & 0xFFFFFF);
        }
    }

    /** Register every item in a tag. Later tag registrations lose to earlier ones on a tie. */
    public static void register(TagKey<Item> tag, int accentRgb) {
        if (tag != null) {
            BY_TAG.add(new TagEntry(tag, accentRgb & 0xFFFFFF));
        }
    }

    /** The registered accent for a stack, or null if nothing claims it. */
    @Nullable
    public static Integer accentFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Integer exact = BY_ITEM.get(stack.getItem());
        if (exact != null) {
            return exact;
        }
        for (TagEntry entry : BY_TAG) {
            if (stack.is(entry.tag())) {
                return entry.accent();
            }
        }
        return null;
    }
}
