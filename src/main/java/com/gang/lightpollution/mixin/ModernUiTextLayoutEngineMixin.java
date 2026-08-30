package com.gang.lightpollution.mixin;

import com.gang.lightpollution.text.EffectStyle;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets ModernUI find the real font behind a dynamic text effect.
 *
 * <p>An effect is carried on a {@code Style} by replacing its font id with a synthetic one, because
 * that is the only field vanilla propagates through text splitting and wrapping untouched. ModernUI
 * also reads that field, to pick a font collection — so without this it looks up a font that does not
 * exist and every styled character falls back to the default face.</p>
 *
 * <p>Only the lookup is decoded. The style keeps its encoded font, because that is what the per-glyph
 * renderer reads to know which effect to draw.</p>
 *
 * <p>{@code @Pseudo} with {@code require = 0}: ModernUI is optional, and the target class is absent
 * when it is not installed.</p>
 */
@Pseudo
@Mixin(targets = "icyllis.modernui.mc.text.TextLayoutEngine", remap = false, priority = 2)
public abstract class ModernUiTextLayoutEngineMixin {
    @ModifyVariable(method = "getFontCollection", at = @At("HEAD"), argsOnly = true, require = 0)
    private ResourceLocation lightPollution$decodeFont(ResourceLocation fontName) {
        return EffectStyle.baseFont(fontName);
    }
}
