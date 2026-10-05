package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.fx.BlackHoleMath;
import java.util.HashSet;
import java.util.UUID;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** One inward shell hit per victim, not another continuous pulling well. */
public final class RadiantCollapseEntity extends BlackHoleEntity {
    private final HashSet<UUID> struck = new HashSet<>();
    public RadiantCollapseEntity(EntityType<? extends RadiantCollapseEntity> type, Level level) { super(type, level); }
    @Override public String configId() { return "radiantCollapse"; }
    @Override public String effectId() { return "radiant_collapse"; }
    public float shellRadius(float partial) {
        return bhIsDisplay() ? bhParameters(partial).bhInfluenceRadius()
                : BlackHoleMath.shellRadius(age(partial), closeTick(), lifetime(), bhParameters(partial).bhInfluenceRadius());
    }
    @Override protected void serverTick(ServerLevel server, int age) {
        if (age < closeTick() || age % 2 != 0) return;
        float radius = bhParameters(0).bhInfluenceRadius();
        float outer = BlackHoleMath.shellRadius(age - 2, closeTick(), lifetime(), radius) + .75F;
        float inner = Math.max(0, shellRadius(0) - .75F);
        var caster = owner(server);
        var targets = server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new AABB(position(), position()).inflate(outer + 1), target -> {
                    double distance = target.getBoundingBox().getCenter().distanceTo(position());
                    return !struck.contains(target.getUUID()) && canAffect(caster, target)
                            && distance <= outer + target.getBbWidth() * .5 && distance >= inner - target.getBbWidth() * .5;
                });
        targets.sort(Comparator.comparingDouble(t -> t.distanceToSqr(position())));
        for (var target : SpellConfig.limitTargets(configId(), targets)) {
            struck.add(target.getUUID()); damage(server, target, secondaryFraction);
        }
    }
}
