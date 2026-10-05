package com.gang.lightpollution.entity;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/** Normal Forge damage events, armour, invulnerability and death hooks remain intact. */
public final class BlackHoleDamage {
    private BlackHoleDamage() {}
    public static DamageSource source(ServerLevel level, BlackHoleEntity effect, Entity caster) {
        var key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, effect.effectId()));
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), effect, caster);
    }
}
