package com.gang.lightpollution.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A server-owned optical attachment, deliberately without a second gravity pulse. */
public final class SchwarzschildLensEntity extends BlackHoleEntity {
    public SchwarzschildLensEntity(EntityType<? extends SchwarzschildLensEntity> type, Level level) { super(type, level); }
    @Override public String configId() { return "schwarzschildLens"; }
    @Override public String effectId() { return "schwarzschild_lens"; }
    @Override protected void serverTick(ServerLevel server, int age) { }
}
