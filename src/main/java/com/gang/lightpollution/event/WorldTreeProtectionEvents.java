package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.entity.WorldTreeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies World Tree's configurable sanctuary protections before damage is committed. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldTreeProtectionEvents {
    private WorldTreeProtectionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || event.isCanceled()
                || (!SpellConfig.worldTreeProtectMagic
                && !SpellConfig.worldTreeProtectProjectile
                && !SpellConfig.worldTreeProtectFire)) {
            return;
        }

        double radius = Math.max(0.1D, SpellConfig.worldTreeEffectRadius);
        if (!level.getEntitiesOfClass(
                WorldTreeEntity.class,
                target.getBoundingBox().inflate(radius),
                tree -> tree.isAlive() && tree.protects(target, event.getSource()))
                .isEmpty()) {
            event.setCanceled(true);
        }
    }
}
