package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.renderer.SpellLightEmitter;
import com.gang.lightpollution.entity.ConstellationEntity;
import com.gang.lightpollution.fx.ConstellationShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client presentation for Constellation.
 *
 * <p>The star's own light is what the shading pass turns into a sweeping cast
 * shadow, so nothing here draws the illumination. All that is left is the star's
 * burning wake, and vanilla particles do that without a render type, a core
 * shader or any vertex work. The entity's renderer is registered in
 * {@code ExampleMod.ClientModEvents} along with every other anchor's.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ConstellationClientEvents {
    private ConstellationClientEvents() {
    }

    /**
     * Spawns the star bodies once per client tick rather than once per frame: at
     * 20 Hz the trail reads as a continuous streak while the particle count stays
     * bounded regardless of framerate.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }

        for (ConstellationEntity entity : SpellLightEmitter.collectConstellations()) {
            int age = entity.getTimelineAgeTicks();
            for (int star = 0; star < ConstellationShape.STAR_COUNT; star++) {
                float brightness = entity.starBrightness(star, 1.0F);
                if (brightness <= 0.05F) {
                    continue;
                }
                Vec3 position = entity.starPosition(star, 1.0F);
                // The star body itself is real geometry drawn by
                // ConstellationWorldRenderer. These particles are only its wake,
                // so they trail slightly behind the current position rather than
                // sitting on it.
                Vec3 wake = position.subtract(
                        entity.starPosition(star, 0.0F)).scale(-0.6D);
                level.addParticle(ParticleTypes.FLAME,
                        position.x + wake.x, position.y + wake.y, position.z + wake.z,
                        0.0D, 0.0D, 0.0D);
                level.addParticle(ParticleTypes.SMALL_FLAME,
                        position.x + wake.x * 2.0D, position.y + wake.y * 2.0D,
                        position.z + wake.z * 2.0D, 0.0D, 0.0D, 0.0D);

                // Streaks falling inward, so the pull is visible before anything
                // is close enough to be dragged. Emitted from a shell rather than
                // the whole volume: what needs showing is the direction, and
                // filling the sphere would just be fog.
                double reach = ConstellationEntity.PULL_RADIUS;
                for (int streak = 0; streak < 3; streak++) {
                    double yaw = level.random.nextDouble() * Math.PI * 2.0D;
                    double pitch = Math.acos(1.0D - 2.0D * level.random.nextDouble());
                    double shell = reach * (0.55D + level.random.nextDouble() * 0.45D);
                    double offsetX = Math.sin(pitch) * Math.cos(yaw) * shell;
                    double offsetY = Math.cos(pitch) * shell;
                    double offsetZ = Math.sin(pitch) * Math.sin(yaw) * shell;
                    // Velocity toward the star, so each streak reads as matter
                    // already on its way in.
                    double speed = 0.28D;
                    level.addParticle(ParticleTypes.END_ROD,
                            position.x + offsetX, position.y + offsetY,
                            position.z + offsetZ,
                            -offsetX / shell * speed, -offsetY / shell * speed,
                            -offsetZ / shell * speed);
                }
            }
        }
    }
}
