package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.entity.CelestialJudgmentEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tracks the last struck position without extending the gameplay anchor or retaining dead entities. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CelestialJudgmentVisuals {
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private CelestialJudgmentVisuals() { }

    public record View(Vec3 feet, float width, float height, float age, float seed, boolean impacted) {
        public CelestialVisualTimeline time() { return new CelestialVisualTimeline(age,
                SpellConfig.celestialImpactTick, SpellConfig.celestialLifetimeTicks, impacted); }
        public Vec3 sky() { return feet.add(0, Math.max(36, height + 19), 0); }
    }

    private static final class Track {
        final ClientLevel level;
        final float seed;
        CelestialJudgmentEntity entity;
        Vec3 feet;
        float width = 0.8F, height = 1.8F, ageAtRemoval;
        long removedAt;
        boolean impacted;
        Track(CelestialJudgmentEntity entity) {
            this.entity = entity;
            level = (ClientLevel) entity.level();
            feet = entity.position();
            seed = entity.getId() % 8192;
        }
        View sample(float partial) {
            float age;
            if (entity != null) {
                age = entity.getVisualAgeTicks(partial);
                impacted |= entity.hasVisualImpact();
                Entity target = level.getEntity(entity.getTargetId());
                if (target instanceof LivingEntity living && living.isAlive()) {
                    feet = living.getPosition(partial);
                    width = Math.max(0.35F, Math.min(12, living.getBbWidth()));
                    height = Math.max(0.5F, Math.min(24, living.getBbHeight()));
                }
            } else age = ageAtRemoval + (level.getGameTime() - removedAt) + partial;
            return new View(feet, width, height, age, seed, impacted);
        }
    }

    static void add(CelestialJudgmentEntity entity) {
        if (entity.level() instanceof ClientLevel) TRACKS.put(entity.getId(), new Track(entity));
    }

    static void remove(CelestialJudgmentEntity entity) {
        Track track = TRACKS.get(entity.getId());
        if (track == null || track.entity != entity) return;
        View view = track.sample(0);
        // Only a server-confirmed kill keeps the brief afterimage. Unloads, cancellation and
        // dimension changes are not kills and must never leave a ghost array behind.
        if (entity.getRemovalReason() == Entity.RemovalReason.DISCARDED
                && entity.hasVisualImpact() && entity.hasVisualKill()
                && view.age() < SpellConfig.celestialLifetimeTicks) {
            track.ageAtRemoval = view.age();
            track.removedAt = track.level.getGameTime();
            track.entity = null;
        } else TRACKS.remove(entity.getId());
    }

    static void clear() { TRACKS.clear(); }

    static List<View> views(float partial) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null) { clear(); return List.of(); }
        var result = new ArrayList<View>();
        var iterator = TRACKS.values().iterator();
        while (iterator.hasNext()) {
            Track track = iterator.next();
            if (track.level != current || track.entity != null && track.entity.isRemoved()) {
                iterator.remove();
                continue;
            }
            View view = track.sample(partial);
            if (view.age() >= SpellConfig.celestialLifetimeTicks) iterator.remove();
            else result.add(view);
        }
        return result;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) views(0); // also expires off-screen/when no light pass runs
    }
}
