package com.gang.lightpollution.mixin;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import com.gang.lightpollution.text.EffectStyle;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 在原版逐字绘制查找字体时还原真实字体 ID。
 * 动态多层效果统一由 ModernUiDynamicTextRenderer 绘制，避免原版与 ModernUI 出现两套不同表现。
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput", priority = 2)
public abstract class FontStringRenderOutputMixin {
    @ModifyArg(
            method = "accept",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;getFontSet(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/FontSet;"
            ),
            index = 0
    )
    private ResourceLocation dynamicTextEffects$decodeGlyphFont(ResourceLocation font) {
        return EffectStyle.baseFont(font);
    }
}
