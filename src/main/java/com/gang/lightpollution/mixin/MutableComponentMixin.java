package com.gang.lightpollution.mixin;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import com.gang.lightpollution.text.DynamicTextParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 兜底处理所有 Component 绘制入口，包括 FTB Library 的 TextField。 */
@Mixin(MutableComponent.class)
public abstract class MutableComponentMixin {
    @Inject(method = "getVisualOrderText", at = @At("HEAD"), cancellable = true)
    private void dynamicTextEffects$parseBeforeVisualOrder(
            CallbackInfoReturnable<FormattedCharSequence> callback
    ) {
        Component self = (Component) (Object) this;
        if (DynamicTextParser.needsParsing(self)) {
            callback.setReturnValue(DynamicTextParser.parse(self).getVisualOrderText());
        }
    }
}
