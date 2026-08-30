package com.gang.lightpollution.mixin;

import com.gang.lightpollution.text.DynamicTextParser;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs the dynamic text codes through FTB Quests' own text entry point.
 *
 * <p>Two injections rather than one, and the order matters. On the way in the codes are swapped for
 * private-use marker characters, so FTB's own parsing — its substitution variables, JSON components,
 * link and image syntax — still sees a string shaped the way it expects. On the way out the finished
 * component is parsed, turning those markers into styled runs.</p>
 *
 * <p>Doing only the second half would mean FTB had already consumed or mangled a {@code &} code by
 * the time we saw it; doing only the first would leave the marker characters visible.</p>
 *
 * <p>{@code @Pseudo} with {@code require = 0}: FTB Quests is optional. The method name is FTB's, and
 * targets version 2001.4.20 — if they rename it the injection is skipped rather than crashing.</p>
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.util.TextUtils", remap = false)
public abstract class FtbTextUtilsMixin {
    @ModifyVariable(method = "parseRawText", at = @At("HEAD"), argsOnly = true, require = 0)
    private static String lightPollution$prepareRawText(String rawText) {
        return DynamicTextParser.prepareForFtb(rawText);
    }

    @Inject(method = "parseRawText", at = @At("RETURN"), cancellable = true, require = 0)
    private static void lightPollution$parseResult(
            String rawText,
            CallbackInfoReturnable<Component> callback
    ) {
        Component result = callback.getReturnValue();
        if (result != null && DynamicTextParser.containsCodes(result.getString())) {
            callback.setReturnValue(DynamicTextParser.parse(result));
        }
    }
}
