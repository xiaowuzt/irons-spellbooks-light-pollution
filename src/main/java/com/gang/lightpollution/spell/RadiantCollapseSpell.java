package com.gang.lightpollution.spell;

import com.gang.lightpollution.entity.RadiantCollapseEntity;
import com.gang.lightpollution.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;

public final class RadiantCollapseSpell extends BlackHoleSpell {
    public RadiantCollapseSpell() { super("radiant_collapse", "radiantCollapse", 240, 1100, 45); }
    @Override protected RadiantCollapseEntity create(ServerLevel server) {
        return new RadiantCollapseEntity(ModEntities.RADIANT_COLLAPSE.get(), server);
    }
}
