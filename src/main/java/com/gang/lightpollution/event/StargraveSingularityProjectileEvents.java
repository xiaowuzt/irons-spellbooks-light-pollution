package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.StargraveSingularityEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Prevents a high-speed projectile from resolving an impact through the core. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StargraveSingularityProjectileEvents {
    private StargraveSingularityProjectileEvents() {
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
                .inflate(StargraveSingularityEntity.EFFECT_RADIUS);

        for (StargraveSingularityEntity singularity : level.getEntitiesOfClass(
                StargraveSingularityEntity.class,
                searchBounds,
                entity -> entity.isAlive() && !entity.isRemoved())) {
            if (singularity.tryAbsorbProjectileImpact(level, projectile, end)) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
                return;
            }
        }
    }
}
