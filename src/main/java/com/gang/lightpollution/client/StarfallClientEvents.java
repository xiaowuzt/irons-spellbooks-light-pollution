package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.renderer.SpellLightEmitter;
import com.gang.lightpollution.entity.StarfallEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla-particle accents for Starfall.
 *
 * <p>The bodies, their trails and the ground shocks are real geometry drawn by
 * {@link com.gang.lightpollution.client.renderer.StarfallWorldRenderer}. What is
 * left here is what geometry is bad at: embers and smoke that drift and settle on
 * their own. The entity's renderer is registered in
 * {@code ExampleMod.ClientModEvents} along with every other anchor's.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class StarfallClientEvents {
    private StarfallClientEvents() {
    }

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

        for (StarfallEntity entity : SpellLightEmitter.collectStarfalls()) {
            int age = entity.getTimelineAgeTicks();
            for (int meteor = 0; meteor < StarfallEntity.METEOR_COUNT; meteor++) {
                float brightness = entity.meteorBrightness(meteor, 1.0F);
                if (brightness <= 0.05F) {
                    continue;
                }
                boolean finale = StarfallEntity.isFinaleMeteor(meteor);
                Vec3 position = entity.meteorPosition(meteor, 1.0F);

                if (age < StarfallEntity.impactTick(meteor)) {
                    // Embers shedding off the body. The trail itself is geometry
                    // now, so these only have to break up its edge: a fixed
                    // column of flame particles was a large part of why a meteor
                    // read as a stack of sprites rather than a falling body.
                    for (int ember = 0; ember < (finale ? 14 : 1); ember++) {
                        double spread = finale ? 3.5D : 0.9D;
                        level.addParticle(ParticleTypes.SMALL_FLAME,
                                position.x + (level.random.nextDouble() - 0.5D) * spread,
                                position.y + level.random.nextDouble() * (finale ? 5.0D : 1.6D),
                                position.z + (level.random.nextDouble() - 0.5D) * spread,
                                0.0D, 0.0D, 0.0D);
                    }
                } else if (age == StarfallEntity.impactTick(meteor)) {
                    level.addParticle(finale
                                    ? ParticleTypes.EXPLOSION_EMITTER
                                    : ParticleTypes.EXPLOSION,
                            position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
                    level.addParticle(ParticleTypes.FLASH,
                            position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
                    if (finale) {
                        // One emitter is a normal creeper-sized blast. This is a
                        // body several blocks across, so the crater has to be
                        // filled out across its own radius.
                        double blast = StarfallEntity.blastRadius(meteor);
                        for (int burst = 0; burst < 8; burst++) {
                            double around = level.random.nextDouble() * Math.PI * 2.0D;
                            double out = level.random.nextDouble() * blast * 0.7D;
                            level.addParticle(ParticleTypes.EXPLOSION_EMITTER,
                                    position.x + Math.cos(around) * out,
                                    position.y + level.random.nextDouble() * 2.0D,
                                    position.z + Math.sin(around) * out,
                                    0.0D, 0.0D, 0.0D);
                        }
                    }
                    for (int dust = 0; dust < (finale ? 40 : 4); dust++) {
                        double around = level.random.nextDouble() * Math.PI * 2.0D;
                        double speed = finale
                                ? 0.35D + level.random.nextDouble() * 0.75D
                                : 0.12D + level.random.nextDouble() * 0.2D;
                        level.addParticle(ParticleTypes.LARGE_SMOKE,
                                position.x, position.y + 0.1D, position.z,
                                Math.cos(around) * speed,
                                finale ? 0.18D : 0.05D,
                                Math.sin(around) * speed);
                    }
                }
            }
        }
    }
}
