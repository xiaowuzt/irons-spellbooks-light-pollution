package com.gang.lightpollution.spell;

import com.gang.lightpollution.entity.SchwarzschildLensEntity;
import com.gang.lightpollution.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;

public final class SchwarzschildLensSpell extends BlackHoleSpell {
    public SchwarzschildLensSpell() { super("schwarzschild_lens", "schwarzschildLens", 180, 700, 35); }
    @Override protected SchwarzschildLensEntity create(ServerLevel server) {
        return new SchwarzschildLensEntity(ModEntities.SCHWARZSCHILD_LENS.get(), server);
    }
}
