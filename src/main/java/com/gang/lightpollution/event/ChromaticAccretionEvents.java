package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.entity.ChromaticAccretionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cross-entity hooks for caster damage storage and high-speed projectile capture. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChromaticAccretionEvents {
    private ChromaticAccretionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || event.isCanceled()
                || event.getAmount() <= 0.0F) {
            return;
        }

        for (ChromaticAccretionEntity effect : level.getEntitiesOfClass(
                ChromaticAccretionEntity.class,
                target.getBoundingBox().inflate(SpellConfig.chromaticAccretionEffectRadius),
                entity -> entity.isAlive() && !entity.isRemoved())) {
            effect.recordCasterDamage(target, event.getSource(), event.getAmount());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level) || projectile.isRemoved()) {
            return;
        }

        Vec3 start = projectile.getBoundingBox().getCenter();
        Vec3 end = event.getRayTraceResult().getLocation();
        AABB searchBounds = new AABB(start, end)
                .inflate(SpellConfig.chromaticAccretionEffectRadius);
        for (ChromaticAccretionEntity effect : level.getEntitiesOfClass(
                ChromaticAccretionEntity.class,
                searchBounds,
                entity -> entity.isAlive() && !entity.isRemoved())) {
            if (effect.tryAbsorbProjectileImpact(level, projectile, end)) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
                return;
            }
        }
    }
}
