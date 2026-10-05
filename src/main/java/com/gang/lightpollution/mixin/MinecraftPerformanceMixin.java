package com.gang.lightpollution.mixin;

import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftPerformanceMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void lightpollution$beginWork(boolean renderLevel, CallbackInfo ci) {
        AdaptiveVisualQuality.beginFrame();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V", shift = At.Shift.BEFORE))
    private void lightpollution$endWork(boolean renderLevel, CallbackInfo ci) {
        AdaptiveVisualQuality.endFrame();
    }
}
