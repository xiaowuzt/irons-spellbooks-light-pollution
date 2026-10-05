package com.gang.lightpollution.spell;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.entity.*;
import com.gang.lightpollution.fx.BlackHoleInstance;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Shared casting plumbing only; each registered spell has an independent entity and shader. */
public abstract class BlackHoleSpell extends AbstractSpell {
    private final ResourceLocation id;
    private final String configId;
    private final DefaultConfig defaults;
    protected BlackHoleSpell(String id, String configId, int cooldown, int mana, int castTicks) {
        this.id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, id); this.configId = configId;
        defaults = new DefaultConfig().setMinRarity(SpellRarity.LEGENDARY).setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
                .setMaxLevel(1).setCooldownSeconds(cooldown).setAllowCrafting(true).build();
        baseManaCost = mana; manaCostPerLevel = 0; baseSpellPower = 0; spellPowerPerLevel = 0; castTime = castTicks;
    }
    protected abstract BlackHoleEntity create(ServerLevel server);
    @Override public ResourceLocation getSpellResource() { return id; }
    @Override public DefaultConfig getDefaultConfig() { return defaults; }
    @Override public CastType getCastType() { return CastType.LONG; }
    @Override public int getManaCost(int level) { return SpellConfig.manaCost(configId); }
    @Override public int getCastTime(int level) { return SpellConfig.castTimeTicks(configId); }
    @Override public int getSpellCooldown() { return SpellConfig.cooldownSeconds(configId) * 20; }
    @Override public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData data) {
        return caster.isAlive() && !(caster instanceof Player p && p.isSpectator());
    }
    @Override public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
        super.onCast(level, spellLevel, caster, source, data);
        if (!(level instanceof ServerLevel server)) return;
        float range = SpellConfig.castRange(configId);
        HitResult hit = Utils.raycastForEntity(server, caster, range, true);
        Vec3 center = hit instanceof EntityHitResult e ? e.getEntity().getBoundingBox().getCenter()
                : hit.getType() != HitResult.Type.MISS ? hit.getLocation() : caster.getEyePosition().add(caster.getViewVector(1).scale(range));
        center = new Vec3(center.x, Math.max(server.getMinBuildHeight() + 2, Math.min(server.getMaxBuildHeight() - 2, center.y + 2)), center.z);
        Vec3 target = center;
        var nearby = server.getEntities(caster, new AABB(target, target).inflate(8), e -> e instanceof BlackHoleInstance bh
                && bh.bhOwnedBy(caster) && !bh.bhIsDisplay());
        if (nearby.stream().anyMatch(e -> e instanceof BlackHoleEntity bh && bh.effectId().equals(id.getPath()) && bh.age(0) <= 2)) return;
        Entity root = nearby.stream().filter(e -> e instanceof BlackHoleInstance bh && bh.bhIsRoot()
                && !(e instanceof RadiantCollapseEntity))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(target))).orElse(null);
        if (root != null) {
            var instance = ((BlackHoleInstance) root).bhParameters(0).bhInstanceId();
            if (nearby.stream().anyMatch(e -> e instanceof BlackHoleEntity bh && bh.effectId().equals(id.getPath())
                    && bh.bhParameters(0).bhInstanceId().equals(instance))) return;
        }
        BlackHoleEntity effect = create(server);
        effect.configure(caster, center, root);
        server.addFreshEntity(effect); // Existing SpellEntityLimitEvents enforces dimension/type budgets.
    }
    @Override public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(Component.translatable("spell." + ExampleMod.MODID + "." + id.getPath() + ".info"));
    }
}
