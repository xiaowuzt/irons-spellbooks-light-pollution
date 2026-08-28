package com.gang.lightpollution.text.world;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * One floating text, and where it is in its animation.
 *
 * <p>Ported from ArcaneVortex's {@code WorldTextPopup}, credited in {@code CREDITS.txt}.</p>
 *
 * <p>Carries both the current and previous value of everything animated, because the animation
 * advances once per tick while rendering happens every frame. Without the previous value the text
 * would step twenty times a second instead of moving.</p>
 */
public final class WorldTextPopup {
    private final Component text;
    private final Vec3 position;
    private final WorldTextConfig config;
    private final long startTick;
    private final Vec3 scatter;
    private final float targetRotation;

    private float scale;
    private float alpha;
    private Vec3 offset = Vec3.ZERO;
    private float rotation;
    private float previousScale;
    private float previousAlpha;
    private Vec3 previousOffset = Vec3.ZERO;
    private float previousRotation;
    private boolean finished;

    public WorldTextPopup(Component text, Vec3 position, WorldTextConfig config, long startTick) {
        this.text = text;
        this.position = position;
        this.config = config;
        this.startTick = startTick;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float angle = config.getRotationMinAngle() == 0.0F && config.getRotationMaxAngle() == 0.0F
                ? config.getRotationAngle()
                : config.getRotationMinAngle() + random.nextFloat()
                        * (config.getRotationMaxAngle() - config.getRotationMinAngle());
        this.targetRotation = angle
                * (config.isRotationRandomDirection() && random.nextBoolean() ? -1.0F : 1.0F);
        if (!config.isRotationAnimated()) {
            this.rotation = this.targetRotation;
            this.previousRotation = this.targetRotation;
        }

        if (config.isRandomOffsetEnabled()) {
            this.scatter = new Vec3(
                    (random.nextFloat() * 2.0F - 1.0F) * config.getRandomOffsetX(),
                    (random.nextFloat() * 2.0F - 1.0F) * config.getRandomOffsetY(),
                    (random.nextFloat() * 2.0F - 1.0F) * config.getRandomOffsetZ());
        } else {
            this.scatter = Vec3.ZERO;
        }
    }

    /** Which phase of the animation a given tick falls in. */
    public enum Phase {
        ENTER, STAY, EXIT, FINISHED
    }

    public Component getText() {
        return text;
    }

    /** Spawn point including the scatter and the configured vertical nudge. */
    public Vec3 getPosition() {
        return position.add(scatter).add(0.0D, config.getYOffset(), 0.0D);
    }

    public WorldTextConfig getConfig() {
        return config;
    }

    public float getTargetRotation() {
        return targetRotation;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean value) {
        this.finished = value;
    }

    public void setScale(float value) {
        this.previousScale = this.scale;
        this.scale = value;
    }

    public void setAlpha(float value) {
        this.previousAlpha = this.alpha;
        this.alpha = value;
    }

    public void setOffset(Vec3 value) {
        this.previousOffset = this.offset;
        this.offset = value;
    }

    public void setRotation(float value) {
        this.previousRotation = this.rotation;
        this.rotation = value;
    }

    public float getRotation() {
        return rotation;
    }

    public float interpolatedScale(float partialTick) {
        return lerp(previousScale, scale, partialTick);
    }

    public float interpolatedAlpha(float partialTick) {
        return lerp(previousAlpha, alpha, partialTick);
    }

    public float interpolatedRotation(float partialTick) {
        return lerp(previousRotation, rotation, partialTick);
    }

    public Vec3 interpolatedOffset(float partialTick) {
        return new Vec3(
                lerp((float) previousOffset.x, (float) offset.x, partialTick),
                lerp((float) previousOffset.y, (float) offset.y, partialTick),
                lerp((float) previousOffset.z, (float) offset.z, partialTick));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    public long age(long currentTick) {
        return currentTick - startTick;
    }

    public Phase phase(long currentTick) {
        long age = age(currentTick);
        long enter = config.getEnterDuration();
        long stay = config.getStayDuration();
        long exit = config.getExitDuration();
        if (age < enter) {
            return Phase.ENTER;
        }
        if (age < enter + stay) {
            return Phase.STAY;
        }
        return age < enter + stay + exit ? Phase.EXIT : Phase.FINISHED;
    }

    /**
     * How far through the current phase, 0 to 1.
     *
     * <p>A zero-length phase divides by zero, which in float gives infinity and clamps to 1 — the
     * phase reads as instantly complete, which is what a zero duration should mean.</p>
     */
    public float phaseProgress(long currentTick) {
        long age = age(currentTick);
        return switch (phase(currentTick)) {
            case ENTER -> Math.min(1.0F, age / (float) config.getEnterDuration());
            case EXIT -> Math.min(1.0F, (age - config.getEnterDuration()
                    - config.getStayDuration()) / (float) config.getExitDuration());
            case STAY, FINISHED -> 1.0F;
        };
    }
}
