package com.gang.lightpollution.entity;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.fx.BlackHoleInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;

/** Local movement stasis, softened pull, whitelisted material absorption. Never changes TPS. */
public final class StasisSingularityEntity extends BlackHoleEntity {
    private static final TagKey<Item> ABSORBABLE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "black_hole_absorbable"));
    private static final TagKey<Block> FRAGILE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "black_hole_fragile"));
    public StasisSingularityEntity(EntityType<? extends StasisSingularityEntity> type, Level level) { super(type, level); }
    @Override public String configId() { return "stasisSingularity"; }
    @Override public String effectId() { return "stasis_singularity"; }
    @Override public boolean bhHasGravity() { return true; }
    @Override protected void serverTick(ServerLevel server, int age) {
        if (age < openTick() || age >= closeTick()) return;
        var p = bhParameters(0);
        boolean gravityOwner = !(rootEntity() instanceof BlackHoleInstance root) || !root.bhHasGravity();
        int interval = Math.max(1, (int) Math.ceil(damageInterval / localTimeScale()));
        boolean pulse = age % interval == 0;
        if (age % 4 != 0 && !pulse) return;
        for (var target : targets(server, p.bhInfluenceRadius())) {
            Vec3 delta = p.bhCenter().subtract(target.getBoundingBox().getCenter());
            double distance = delta.length();
            if (age % 4 == 0) {
                int amplifier = Math.max(0, Math.min(4, Math.round((1 - localTimeScale()) / .15F) - 1));
                if (localTimeScale() < .99F) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, amplifier, false, true));
                if (gravityOwner && distance > .05) {
                    double force = .045 + .12 * Math.pow(1 - Math.min(1, distance / p.bhInfluenceRadius()), 2);
                    Vec3 velocity = target.getDeltaMovement().scale(.85).add(delta.scale(force / distance));
                    if (velocity.lengthSqr() > .81) velocity = velocity.normalize().scale(.9);
                    target.setDeltaMovement(velocity); target.hurtMarked = true;
                }
            }
            if (pulse) damage(server, target, distance < p.bhHorizonRadius() ? secondaryFraction : damageFraction);
        }
        if (gravityOwner && age % 4 == 0 && absorbItems) absorb(server, p.bhHorizonRadius(), p.bhInfluenceRadius());
        if (gravityOwner && age % 20 == 0 && breakBlocks) breakFragile(server, p.bhHorizonRadius());
    }
    private void absorb(ServerLevel server, float horizon, float radius) {
        // Never delete arbitrary entities or player inventories. Valuable/NBT-bearing items are excluded.
        var items = server.getEntitiesOfClass(ItemEntity.class, new AABB(position(), position()).inflate(radius),
                item -> item.isAlive() && !item.isInvulnerable() && !item.getItem().hasTag() && item.getItem().is(ABSORBABLE));
        items.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(position())));
        int count = 0;
        for (var item : items) {
            if (++count > Math.min(32, SpellConfig.serverTargetScanLimit)) break;
            Vec3 delta = position().subtract(item.position()); double d = delta.length();
            if (d > radius) continue;
            if (d <= Math.max(.15, horizon * .8)) { item.discard(); continue; }
            Vec3 velocity = item.getDeltaMovement().scale(.85).add(delta.scale(.14 / d));
            item.setDeltaMovement(velocity.lengthSqr() > 1 ? velocity.normalize() : velocity); item.hurtMarked = true;
        }
    }
    private void breakFragile(ServerLevel server, float horizon) {
        if (!(owner(server) instanceof ServerPlayer player) || !player.getAbilities().mayBuild
                || !server.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        int radius = Math.min(4, Math.max(1, (int) Math.ceil(horizon))), count = 0;
        BlockPos center = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (Vec3.atCenterOf(pos).distanceToSqr(position()) > horizon * horizon || !server.hasChunkAt(pos)) continue;
            var state = server.getBlockState(pos);
            if (!state.is(FRAGILE) || state.hasBlockEntity() || state.getDestroySpeed(server, pos) < 0
                    || !server.mayInteract(player, pos)) continue;
            // Respect Forge protection mods. Only a player-owned cast is eligible.
            if (ForgeHooks.onBlockBreakEvent(server, player.gameMode.getGameModeForPlayer(), player, pos) == -1) continue;
            server.destroyBlock(pos, false, player); // absorbed, no item/explosion chain
            if (++count >= 8) break;
        }
    }
}
