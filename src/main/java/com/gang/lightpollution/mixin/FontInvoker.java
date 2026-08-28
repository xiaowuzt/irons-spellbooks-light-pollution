package com.gang.lightpollution.mixin;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Font.class, priority = 2)
public interface FontInvoker {
    @Invoker("getFontSet")
    FontSet dynamicTextEffects$getFontSet(ResourceLocation font);

    @Invoker("drawInternal")
    int dynamicTextEffects$drawInternal(
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int backgroundColor,
            int packedLight
    );

    @Invoker("renderChar")
    void dynamicTextEffects$renderChar(
            BakedGlyph glyph,
            boolean bold,
            boolean italic,
            float boldOffset,
            float x,
            float y,
            Matrix4f pose,
            VertexConsumer buffer,
            float red,
            float green,
            float blue,
            float alpha,
            int packedLight
    );
}
