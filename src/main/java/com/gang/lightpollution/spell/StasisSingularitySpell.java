package com.gang.lightpollution.spell;

import com.gang.lightpollution.entity.StasisSingularityEntity;
import com.gang.lightpollution.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;

public final class StasisSingularitySpell extends BlackHoleSpell {
    public StasisSingularitySpell() { super("stasis_singularity", "stasisSingularity", 300, 1300, 45); }
    @Override protected StasisSingularityEntity create(ServerLevel server) {
        return new StasisSingularityEntity(ModEntities.STASIS_SINGULARITY.get(), server);
    }
}
