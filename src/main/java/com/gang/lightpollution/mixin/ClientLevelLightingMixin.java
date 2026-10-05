package com.gang.lightpollution.mixin;

import com.gang.lightpollution.client.renderer.SpellLightPostProcessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Include remote/server block updates, not only the local player's interaction events. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelLightingMixin {
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void lightpollution$dirtyBlock(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        SpellLightPostProcessor.requestVoxelRefresh(pos);
    }
}
