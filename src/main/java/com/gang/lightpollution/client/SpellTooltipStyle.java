package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Styles the whole tooltip for this mod's items, frame included.
 *
 * <p>One flat accent colour per spell, applied to every line and to the border. Two earlier
 * attempts are worth recording because both looked worse than doing nothing:</p>
 *
 * <p>Animated effect codes on just the name and the stat line left every other line — level,
 * rarity, cast time, mana, cooldown, school — in Iron's Spells' own orange, blue, grey and red.
 * Those lines come from library code no lang file here can reach, so half the box was restyled and
 * it clashed with itself.</p>
 *
 * <p>Putting the animated code on every line fixed the coverage and not the look: an effect that
 * sweeps through hues gives adjacent characters different colours, so a single sentence came out
 * multicoloured.</p>
 *
 * <p>What works is the split: the animated code on the name only, and a flat accent on everything
 * below it. The name is one short string where per-character motion reads as deliberate, and the
 * stats are sentences where it reads as broken. Keeping the code in the name string also covers the
 * item name that floats above the hotbar, which the tooltip event never sees.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SpellTooltipStyle {
    private SpellTooltipStyle() {
    }

    /**
     * Pull the tooltip onto the spell's accent colour, leaving the name animated.
     *
     * <p>Every line except the first. The first is the item name, which carries an animated effect
     * code from the lang file, and restyling it here would strip that — the name is meant to move
     * while the stats behind it stay one flat colour.</p>
     *
     * <p>The rest matters because those lines are not ours. Level, rarity, cast time, mana,
     * cooldown and school all come from Iron's Spells in its own orange, blue, grey and red, and no
     * lang file here can reach them. The tooltip event hands over the finished list, which is the
     * only place they can be touched.</p>
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Integer boxed = accentFor(event.getItemStack());
        if (boxed == null) {
            return;
        }
        int accent = boxed;
        int dim = shade(accent, 0.62F);

        List<Component> lines = event.getToolTip();
        for (int index = 1; index < lines.size(); ++index) {
            Component line = lines.get(index);
            if (line == null || line.getString().isEmpty()) {
                continue;
            }
            // The mod's own name line, which Iron's Spells prints in italic grey. Dimmed rather
            // than full accent so it stays subordinate to the spell's own text.
            String text = line.getString();
            boolean brand = text.contains(ExampleMod.MODID)
                    || text.contains("Light Pollution")
                    || text.contains("光污染");
            lines.set(index, recolour(line, brand ? dim : accent, brand));
        }
    }

    /**
     * Apply a colour to a component and everything nested in it.
     *
     * <p>Rebuilt rather than restyled in place: a tooltip line from Iron's Spells is usually a
     * translatable component with coloured children, and setting a style on the parent leaves every
     * child's own colour winning. Walking the tree is the only way a whole line actually changes —
     * without it the label recolours and the number at the end of the line keeps its old colour,
     * which is the narrower version of the same complaint.</p>
     */
    private static Component recolour(Component line, int colour, boolean italic) {
        Style style = line.getStyle()
                .withColor(TextColor.fromRgb(colour))
                .withItalic(italic ? Boolean.TRUE : line.getStyle().isItalic());
        MutableComponent rebuilt = line.plainCopy().setStyle(style);
        for (Component child : line.getSiblings()) {
            rebuilt.append(recolour(child, colour, italic));
        }
        return rebuilt;
    }

    /**
     * Drive the frame from the same accent, animated.
     *
     * <p>Same mechanism ArcaneVortex uses for its own frames: the border is two colours and the
     * event lets them be replaced per stack. Sliding them against each other over time is what
     * makes the frame read as alive rather than as a recoloured box.</p>
     */
    @SubscribeEvent
    public static void onTooltipColour(RenderTooltipEvent.Color event) {
        Integer boxed = accentFor(event.getItemStack());
        if (boxed == null) {
            return;
        }
        int accent = boxed;
        if (com.gang.lightpollution.SpellLightConfig.tooltipStyle
                == com.gang.lightpollution.SpellLightConfig.TooltipStyle.PANEL) {
            // The custom panel is drawn behind this, so vanilla's own border and background have
            // to get out of the way or they sit on top of it. Only PANEL needs this: ARCANE cancels
            // the Pre event, and this one is fired from inside the background pass that Pre gates,
            // so it never reaches here in that style.
            event.setBorderStart(0);
            event.setBorderEnd(0);
            event.setBackgroundStart(0);
            event.setBackgroundEnd(0);
            return;
        }
        // Phase from wall time rather than from a tick counter, so it keeps moving while the
        // game is paused and the tooltip is the only thing on screen.
        float phase = (System.currentTimeMillis() % 4000L) / 4000.0F;
        float wave = (float) (Math.sin(phase * Math.PI * 2.0) * 0.5 + 0.5);
        event.setBorderStart(0xFF000000 | shade(accent, 0.45F + wave * 0.55F));
        event.setBorderEnd(0xFF000000 | shade(accent, 1.0F - wave * 0.55F));
    }

    /**
     * The accent for one of our items, or null if the stack is not ours. Shared with the frame.
     *
     * <p>The table itself lives in {@link com.gang.lightpollution.SpellPalette}, in the common
     * package, because the floating damage text needs the same colours and has to pick them on the
     * server where damage is resolved.</p>
     */
    static Integer accentFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !ExampleMod.MODID.equals(id.getNamespace())) {
            return null;
        }
        return com.gang.lightpollution.SpellPalette.paletteFor(id.getPath()) == null
                ? null
                : com.gang.lightpollution.SpellPalette.accentFor(id.getPath());
    }

    /** Scale a packed RGB toward black or white, clamped. */
    private static int shade(int rgb, float factor) {
        int r = clamp((int) (((rgb >> 16) & 0xFF) * factor));
        int g = clamp((int) (((rgb >> 8) & 0xFF) * factor));
        int b = clamp((int) ((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}
