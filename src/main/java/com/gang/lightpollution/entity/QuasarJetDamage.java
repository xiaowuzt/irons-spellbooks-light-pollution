package com.gang.lightpollution.entity;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Registry key and source factory for quasar_jet. */
public final class QuasarJetDamage {
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "quasar_jet"));

    private QuasarJetDamage() {
    }

    public static DamageSource source(ServerLevel level, Entity caster, Entity effect) {
        Registry<DamageType> registry =
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> holder = registry.getHolderOrThrow(TYPE);
        return new DamageSource(holder, effect, caster);
    }
}
