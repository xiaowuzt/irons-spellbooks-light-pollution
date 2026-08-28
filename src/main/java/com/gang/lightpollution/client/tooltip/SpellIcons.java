package com.gang.lightpollution.client.tooltip;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellPalette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Finds a spell's own icon, for the tooltip styles that show one.
 *
 * <p>The icons already exist — twenty-four of them, drawn for the spell book's own UI — and their
 * filenames match the registry paths exactly, which is what makes this a lookup rather than a table.
 * Using them is better than importing the source mod's art anyway: those were illustrations of
 * specific weapons, and a fox blade behind an astronomy spell's tooltip would be absurd.</p>
 */
public final class SpellIcons {
    private static final String PREFIX = "textures/gui/spell_icons/";

    private SpellIcons() {
    }

    /** The icon for one of our items, or null if the stack is not ours or has no icon. */
    public static ResourceLocation forStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !ExampleMod.MODID.equals(id.getNamespace())) {
            return null;
        }
        String path = id.getPath();
        if (path.endsWith("_scroll")) {
            path = path.substring(0, path.length() - "_scroll".length());
        }
        // Only the spells with a palette entry are ours to decorate, and those are exactly the ones
        // with an icon on disk, so this doubles as the existence check.
        if (SpellPalette.paletteFor(path) == null) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, PREFIX + path + ".png");
    }
}
