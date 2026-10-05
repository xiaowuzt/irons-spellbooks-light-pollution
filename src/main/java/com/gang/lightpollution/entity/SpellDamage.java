package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellPalette;
import com.gang.lightpollution.net.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Damage that ignores armour, resistance and absorption, and reports itself to the client.
 *
 * <p>This replaces twenty byte-identical copies of a {@code private static applyTrueDamage}, one per
 * anchor entity. Twenty copies of anything is a liability, but the reason it had to become one is
 * the floating text: the client cannot see damage resolved inside a server-only entity tick, so
 * something has to send it, and that something needs to be in exactly one place.</p>
 *
 * <p>Stargrave Singularity keeps its own version deliberately. That one works from a pre-computed
 * snapshot and carries extra handling for bosses that cancel {@code hurt}, so it is a different
 * method that happens to share a name rather than another copy.</p>
 *
 * <p>Why the health is corrected after {@code hurt} rather than instead of it: going through
 * {@code hurt} is what gives Forge its events, combat attribution and the normal death path. But
 * modded bosses routinely cancel or reduce it for shields and phase gates, so the health is set
 * afterwards to what it should have been. Both halves are load-bearing.</p>
 */
public final class SpellDamage {
    private SpellDamage() {
    }

    /**
     * Deal {@code fraction} of the target's maximum health, and show it.
     *
     * @param anchor the spell's anchor entity, which is what the colour is looked up from
     */
    public static void apply(Entity anchor, LivingEntity target, DamageSource source,
                             float fraction) {
        // Zero is a disabled channel, not a zero-damage hurt event that strips absorption.
        if (!Float.isFinite(fraction) || fraction <= 0.0F) return;
        float damage = Math.max(0.0F, target.getMaxHealth() * fraction);
        float desiredHealth = Math.max(0.0F, target.getHealth() - damage);
        float before = target.getHealth();

        target.invulnerableTime = 0;
        target.hurt(source, damage);
        target.invulnerableTime = 0;

        if (!target.isDeadOrDying() && !target.isRemoved()) {
            target.setAbsorptionAmount(0.0F);
            float finalHealth = Math.min(target.getHealth(), desiredHealth);
            if (finalHealth <= 0.0F) {
                target.setHealth(0.0F);
                if (!target.isRemoved()) {
                    target.die(source);
                }
            } else {
                target.setHealth(finalHealth);
            }
        }

        // What actually came off, not what was asked for: a boss that shrugged half of it off
        // should show the half it took. One expression for every path, including death — a target
        // that died lost exactly the health it had, which this already says.
        report(anchor, target, Math.max(0.0F, before - target.getHealth()));
    }

    private static void report(Entity anchor, LivingEntity target, float dealt) {
        if (dealt <= 0.0F || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        ModNetwork.sendDamageText(level, target.position(), target.getId(), dealt,
                SpellPalette.accentFor(anchor));
    }
}
