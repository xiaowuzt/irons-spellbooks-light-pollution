package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Admission guard for spell effect entities.
 *
 * <p>A server can have several players casting the large visual spells at once. Without a
 * bound, every cast adds another per-tick AABB scan and another client-side light source. This
 * guard refuses new effect entities after the configured level/type budget is exhausted; normal
 * mobs and all already active spells continue unaffected.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellEntityLimitEvents {
    private static final Map<Level, Set<Entity>> ACTIVE =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private SpellEntityLimitEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        Entity entity = event.getEntity();
        if (level.isClientSide || !isSpellEntity(entity)) {
            return;
        }
        synchronized (ACTIVE) {
            Set<Entity> active = ACTIVE.computeIfAbsent(level,
                    ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
            if (active.contains(entity)) {
                return;
            }
            EntityType<?> type = entity.getType();
            long sameType = active.stream().filter(value -> value.getType() == type).count();
            var key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            int typeLimit = SpellConfig.entityLimitForRegistryPath(key == null ? null : key.getPath());
            if (active.size() >= SpellConfig.activeEntityLimit()
                    || sameType >= typeLimit) {
                event.setCanceled(true);
                entity.discard();
                return;
            }
            active.add(entity);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide || !isSpellEntity(event.getEntity())) {
            return;
        }
        synchronized (ACTIVE) {
            Set<Entity> active = ACTIVE.get(level);
            if (active == null) {
                return;
            }
            active.remove(event.getEntity());
            if (active.isEmpty()) {
                ACTIVE.remove(level);
            }
        }
    }

    private static boolean isSpellEntity(Entity entity) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key != null && ExampleMod.MODID.equals(key.getNamespace());
    }
}
