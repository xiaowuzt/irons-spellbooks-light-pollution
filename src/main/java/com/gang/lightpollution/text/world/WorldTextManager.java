package com.gang.lightpollution.text.world;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Holds the floating texts currently on screen, and merges damage before they become one.
 *
 * <p>Ported from ArcaneVortex's {@code WorldTextManager}, credited in {@code CREDITS.txt}. Client
 * only — everything here reads {@code Minecraft.getInstance()}.</p>
 *
 * <p>The merging is not in the original and is needed here. Spells in this pack tick damage rather
 * than landing single hits: the Crab Nebula alone resolves its filaments every 7 ticks and its
 * interior wind every 10, so one target inside one cast would produce around forty separate numbers.
 * Damage is therefore accumulated per target for {@link #MERGE_TICKS} and spawned once as a total.
 * That delays the first number by half a second, which is the price of it being readable.</p>
 */
public final class WorldTextManager {
    /** How long damage on one target is accumulated before it is shown, in ticks. */
    public static final int MERGE_TICKS = 10;

    private static final List<WorldTextPopup> active = new ArrayList<>();
    private static final Map<Integer, Pending> pending = new HashMap<>();

    /** Damage on one target that has not been shown yet. */
    private static final class Pending {
        private float total;
        private int colour;
        private long dueTick;
    }

    private WorldTextManager() {
    }

    /** Show a text at a world position now. */
    public static void spawn(Component text, Vec3 position, WorldTextConfig config) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        active.add(new WorldTextPopup(text, position, config, minecraft.level.getGameTime()));
    }

    public static void spawn(Component text, Vec3 position) {
        spawn(text, position, WorldTextConfig.createDefault());
    }

    /**
     * Add damage to a target's running total, to be shown when the merge window closes.
     *
     * <p>Keyed on the entity rather than a position, because targets move — a position key would
     * split one creature's damage across several piles as it walked.</p>
     */
    public static void reportDamage(int entityId, float amount, int colour) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || amount <= 0.0F) {
            return;
        }
        Pending entry = pending.computeIfAbsent(entityId, id -> new Pending());
        entry.total += amount;
        entry.colour = colour;
        if (entry.dueTick == 0L) {
            entry.dueTick = minecraft.level.getGameTime() + MERGE_TICKS;
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();

        Iterator<Map.Entry<Integer, Pending>> due = pending.entrySet().iterator();
        while (due.hasNext()) {
            Map.Entry<Integer, Pending> entry = due.next();
            Pending value = entry.getValue();
            if (now < value.dueTick) {
                continue;
            }
            due.remove();
            Entity target = minecraft.level.getEntity(entry.getKey());
            if (target == null) {
                // Out of range or dead client-side. Nothing to attach a number to.
                continue;
            }
            spawn(damageText(value.total, value.colour),
                    target.getBoundingBox().getCenter(), damageStyle(value.total));
        }

        Iterator<WorldTextPopup> live = active.iterator();
        while (live.hasNext()) {
            WorldTextPopup popup = live.next();
            WorldTextAnimator.update(popup, now);
            if (popup.isFinished()) {
                live.remove();
            }
        }
    }

    private static Component damageText(float total, int colour) {
        String shown = total >= 10.0F
                ? String.valueOf(Math.round(total))
                : String.format("%.1f", total);
        return Component.literal(shown).withStyle(style -> style.withColor(colour));
    }

    /**
     * Bigger and more emphatic the harder it hit.
     *
     * <p>The scatter is what keeps two targets standing together from overprinting each other.</p>
     */
    private static WorldTextConfig damageStyle(float total) {
        float weight = Math.min(1.0F, total / 40.0F);
        return new WorldTextConfig()
                .setScale(0.7F + weight * 0.8F)
                .setEnterAnimation(WorldTextConfig.AnimationType.ELASTIC_IN)
                .setExitAnimation(WorldTextConfig.AnimationType.SLIDE_DOWN)
                .setEnterDuration(6L)
                .setStayDuration(14L)
                .setExitDuration(10L)
                .setYOffset(0.9F)
                .setShadow(true)
                .setRotationRandomRange(-9.0F, 9.0F)
                .setRotationRandomDirection(true)
                .setRandomOffset(0.45F, 0.25F, 0.45F);
    }

    /** Everything on screen, for the renderer to walk. */
    public static List<WorldTextPopup> activePopups() {
        return active;
    }

    /** Dropped on world change, or the list would outlive the level the positions referred to. */
    public static void clear() {
        active.clear();
        pending.clear();
    }
}
