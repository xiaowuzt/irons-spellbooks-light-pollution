package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.EclipseSeveranceEntity;

import java.util.Random;

/**
 * Client-side animation state for one Eclipse Severance cast.
 *
 * <p>This keeps the timing, particle integration, bolt generation and random
 * call order of Gemini's {@code SweepAttackInstance}.  Rendering code should
 * only read this state; it must not derive a second, tick-based timeline.</p>
 */
public final class EclipseSeveranceVisualInstance {
    public static final int MAX_PARTICLES = 128;
    public static final int MAX_BOLTS = 12;
    public static final int MAX_BOLT_SEGMENTS = 7;

    public static final int PARTICLE_COUNT = 88;
    public static final int LIGHTNING_BOLTS = 7;
    public static final float HEIGHT_OFFSET = 1.05F;
    public static final float DURATION_MS = 1450.0F;
    public static final float DEFAULT_ARC_DEGREES = 145.0F;
    public static final float DEFAULT_ANIMATION_SPEED = 1.0F;
    public static final float DEFAULT_PARTICLE_SPEED = 6.5F;
    public static final float DEFAULT_PARTICLE_SPREAD = 0.7F;
    public static final float DEFAULT_PARTICLE_SIZE = 0.18F;
    public static final float DEFAULT_PARTICLE_GRAVITY = 2.6F;
    private static final float CLIENT_TICK_SECONDS = 0.05F;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0D;

    public final float[] particleX = new float[MAX_PARTICLES];
    public final float[] particleY = new float[MAX_PARTICLES];
    public final float[] particleZ = new float[MAX_PARTICLES];
    public final float[] particleVX = new float[MAX_PARTICLES];
    public final float[] particleVY = new float[MAX_PARTICLES];
    public final float[] particleVZ = new float[MAX_PARTICLES];
    public final float[] particleLife = new float[MAX_PARTICLES];
    public final float[] particleMaxLife = new float[MAX_PARTICLES];
    public final float[] particleSize = new float[MAX_PARTICLES];
    public int particleCount;

    public final float[][] boltX = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public final float[][] boltY = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public final float[][] boltZ = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public final int[] boltSegments = new int[MAX_BOLTS];
    public int boltCount;

    public final long startTimeNanos;
    public final double x;
    public final double y;
    public final double z;
    public final float facingYawDegrees;
    public final int synchronizedSeed;
    public final float dirX;
    public final float dirZ;
    public final float arcStart;
    public final float arcEnd;
    public final float seed;
    public boolean alive = true;

    private final float totalDurationMs;
    private final float sweepDurationMs;
    private final float particleDurationMs;
    private final float ringDurationMs;
    private final float fadeOutStartMs;
    private final float particleGravity;
    private float simulatedParticleAgeSeconds;

    /** Creates a visual instance from the synchronized spell entity. */
    public EclipseSeveranceVisualInstance(EclipseSeveranceEntity entity) {
        this(entity.getX(), entity.getY(), entity.getZ(), entity.getFacingYaw(), entity.getSeed(),
                Math.max(0.0F, entity.tickCount * CLIENT_TICK_SECONDS), System.nanoTime());
    }

    /** Creates a newly started instance using the monotonic client clock. */
    public EclipseSeveranceVisualInstance(double x, double y, double z,
                                          float facingYawDegrees, int synchronizedSeed) {
        this(x, y, z, facingYawDegrees, synchronizedSeed, 0.0F, System.nanoTime());
    }

    /**
     * Test/replay overload with an explicit initial age and monotonic clock
     * sample. The synchronized seed replaces Gemini's local timestamp seed
     * component while the remaining mixing and Random call order stay equal.
     */
    public EclipseSeveranceVisualInstance(double x, double y, double z,
                                          float facingYawDegrees, int synchronizedSeed,
                                          float initialAgeSeconds, long nowNanos) {
        float initialAge = Math.max(0.0F, initialAgeSeconds);
        this.startTimeNanos = nowNanos - (long) (initialAge * NANOS_PER_SECOND);
        this.x = x;
        this.y = y + HEIGHT_OFFSET;
        this.z = z;
        this.facingYawDegrees = facingYawDegrees;
        this.synchronizedSeed = synchronizedSeed;

        float yaw = facingYawDegrees * (float) (Math.PI / 180.0D);
        this.dirX = (float) -Math.sin(yaw);
        this.dirZ = (float) Math.cos(yaw);
        float baseAngle = (float) Math.atan2(dirZ, dirX);
        float range = (float) Math.toRadians(DEFAULT_ARC_DEGREES);
        this.arcStart = baseAngle - range * 0.5F;
        this.arcEnd = baseAngle + range * 0.5F;

        float speed = Math.max(0.1F, DEFAULT_ANIMATION_SPEED);
        this.totalDurationMs = Math.max(120.0F, DURATION_MS / speed);
        this.sweepDurationMs = Math.max(55.0F, totalDurationMs * 0.18F);
        this.particleDurationMs = totalDurationMs * 0.72F;
        this.ringDurationMs = totalDurationMs * 0.78F;
        this.fadeOutStartMs = totalDurationMs * 0.48F;
        this.particleGravity = DEFAULT_PARTICLE_GRAVITY;

        long mixedSeed = Integer.toUnsignedLong(synchronizedSeed)
                ^ Double.doubleToLongBits(x * 17.0D + z * 31.0D)
                ^ ((long) Float.floatToIntBits(arcStart) << 32);
        Random random = new Random(mixedSeed);
        this.seed = random.nextFloat() * 1024.0F;

        // Keep this order identical to Gemini: particles first, then bolts.
        spawnParticles(random, PARTICLE_COUNT, DEFAULT_PARTICLE_SPEED,
                DEFAULT_PARTICLE_SPREAD, DEFAULT_PARTICLE_SIZE);
        spawnLightning(random, LIGHTNING_BOLTS);
        advanceTo(initialAge);
    }

    public float ageSeconds() {
        return ageSeconds(System.nanoTime());
    }

    public float ageSeconds(long nowNanos) {
        return (float) Math.max(0.0D, (nowNanos - startTimeNanos) / NANOS_PER_SECOND);
    }

    public float effectProgress() {
        return effectProgress(ageSeconds());
    }

    public float effectProgress(float ageSeconds) {
        return clamp(ageSeconds * 1000.0F / totalDurationMs, 0.0F, 1.0F);
    }

    public float sweepProgress() {
        return sweepProgress(ageSeconds());
    }

    public float sweepProgress(float ageSeconds) {
        float t = clamp(ageSeconds * 1000.0F / sweepDurationMs, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - t, 3.2D);
    }

    public float particleAlpha() {
        return particleAlpha(ageSeconds());
    }

    public float particleAlpha(float ageSeconds) {
        float t = clamp(ageSeconds * 1000.0F / particleDurationMs, 0.0F, 1.0F);
        return 1.0F - smoothstep(0.25F, 1.0F, t);
    }

    public float ringProgress() {
        return ringProgress(ageSeconds());
    }

    public float ringProgress(float ageSeconds) {
        float t = clamp(ageSeconds * 1000.0F / ringDurationMs, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - t, 2.4D);
    }

    public float ringAlpha() {
        return ringAlpha(ageSeconds());
    }

    public float ringAlpha(float ageSeconds) {
        float t = effectProgress(ageSeconds);
        return smoothstep(0.0F, 0.08F, t)
                * (1.0F - smoothstep(0.52F, 1.0F, t));
    }

    public float lightningAlpha() {
        return lightningAlpha(ageSeconds());
    }

    public float lightningAlpha(float ageSeconds) {
        float t = effectProgress(ageSeconds);
        float flicker = 0.78F + 0.22F * (float) Math.sin(ageSeconds * 54.0F + seed);
        return (1.0F - smoothstep(0.18F, 0.82F, t)) * flicker;
    }

    public float arcAlpha() {
        return arcAlpha(ageSeconds());
    }

    public float arcAlpha(float ageSeconds) {
        float elapsed = ageSeconds * 1000.0F;
        float fadeIn = smoothstep(0.0F, Math.min(90.0F, sweepDurationMs), elapsed);
        float fadeOut = 1.0F;
        if (elapsed > fadeOutStartMs) {
            fadeOut = 1.0F - smoothstep(fadeOutStartMs, totalDurationMs, elapsed);
        }
        return fadeIn * fadeOut;
    }

    public float burstAlpha() {
        return burstAlpha(ageSeconds());
    }

    public float burstAlpha(float ageSeconds) {
        float t = effectProgress(ageSeconds);
        return (1.0F - smoothstep(0.08F, 0.46F, t))
                * smoothstep(0.0F, 0.035F, t);
    }

    public boolean isAlive() {
        return isAlive(ageSeconds());
    }

    public boolean isAlive(float ageSeconds) {
        return alive && ageSeconds * 1000.0F < totalDurationMs;
    }

    public float totalDurationMs() {
        return totalDurationMs;
    }

    public float sweepDurationMs() {
        return sweepDurationMs;
    }

    public float particleDurationMs() {
        return particleDurationMs;
    }

    public float ringDurationMs() {
        return ringDurationMs;
    }

    public float fadeOutStartMs() {
        return fadeOutStartMs;
    }

    public double originX() {
        return x;
    }

    public double originY() {
        return y;
    }

    public double originZ() {
        return z;
    }

    public float facingYawDegrees() {
        return facingYawDegrees;
    }

    public int synchronizedSeed() {
        return synchronizedSeed;
    }

    public int particleCount() {
        return particleCount;
    }

    public float particleX(int index) {
        return particleX[index];
    }

    public float particleY(int index) {
        return particleY[index];
    }

    public float particleZ(int index) {
        return particleZ[index];
    }

    public float particleLife(int index) {
        return particleLife[index];
    }

    public float particleMaxLife(int index) {
        return particleMaxLife[index];
    }

    public float particleSize(int index) {
        return particleSize[index];
    }

    public int boltCount() {
        return boltCount;
    }

    public int boltSegments(int bolt) {
        return boltSegments[bolt];
    }

    public float boltX(int bolt, int segment) {
        return boltX[bolt][segment];
    }

    public float boltY(int bolt, int segment) {
        return boltY[bolt][segment];
    }

    public float boltZ(int bolt, int segment) {
        return boltZ[bolt][segment];
    }

    /**
     * Advances CPU particles to the requested visual age in 50 ms client-tick
     * steps, matching the original LocalPlayer tick update cadence.
     */
    public void advanceTo(float ageSeconds) {
        float targetAge = clamp(ageSeconds, 0.0F, totalDurationMs / 1000.0F);
        while (simulatedParticleAgeSeconds + CLIENT_TICK_SECONDS <= targetAge + 1.0E-6F) {
            updateParticles(CLIENT_TICK_SECONDS);
        }
    }

    public void updateParticles(float dt) {
        if (dt <= 0.0F) {
            return;
        }
        simulatedParticleAgeSeconds += dt;
        for (int i = 0; i < particleCount; i++) {
            particleLife[i] -= dt;
            if (particleLife[i] <= 0.0F) {
                continue;
            }

            particleX[i] += particleVX[i] * dt;
            particleY[i] += particleVY[i] * dt;
            particleZ[i] += particleVZ[i] * dt;
            particleVY[i] -= particleGravity * dt;

            float drag = (float) Math.pow(0.965F, dt * 20.0F);
            particleVX[i] *= drag;
            particleVY[i] *= drag;
            particleVZ[i] *= drag;
        }
    }

    public void discard() {
        alive = false;
    }

    private void spawnParticles(Random random, int requestedCount, float speed,
                                float spread, float size) {
        particleCount = Math.min(Math.max(requestedCount, 0), MAX_PARTICLES);
        float angleRange = positiveAngleRange();

        for (int i = 0; i < particleCount; i++) {
            float t = random.nextFloat();
            float angle = arcStart + angleRange * t;
            float radius = 0.7F + random.nextFloat() * 1.45F;

            particleX[i] = (float) (x + Math.cos(angle) * radius);
            particleY[i] = (float) (y + (random.nextFloat() - 0.35F) * 0.55F);
            particleZ[i] = (float) (z + Math.sin(angle) * radius);

            float velocity = speed * (0.62F + random.nextFloat() * 0.76F);
            float cone = Math.max(0.0F, spread);
            particleVX[i] = dirX * velocity + (random.nextFloat() - 0.5F) * cone * velocity;
            particleVY[i] = (random.nextFloat() - 0.2F) * velocity * cone * 0.55F;
            particleVZ[i] = dirZ * velocity + (random.nextFloat() - 0.5F) * cone * velocity;

            particleMaxLife[i] = 0.24F + random.nextFloat() * 0.62F;
            particleLife[i] = particleMaxLife[i];
            particleSize[i] = size * (0.55F + random.nextFloat() * 0.9F);
        }
    }

    private void spawnLightning(Random random, int requestedBolts) {
        boltCount = Math.min(Math.max(requestedBolts, 0), MAX_BOLTS);
        float angleRange = positiveAngleRange();

        for (int b = 0; b < boltCount; b++) {
            float baseAngle = arcStart + angleRange * random.nextFloat();
            float baseRadius = 0.8F + random.nextFloat() * 1.55F;
            float tangentX = (float) -Math.sin(baseAngle);
            float tangentZ = (float) Math.cos(baseAngle);

            float cx = (float) (x + Math.cos(baseAngle) * baseRadius);
            float cy = (float) (y + (random.nextFloat() - 0.25F) * 0.7F);
            float cz = (float) (z + Math.sin(baseAngle) * baseRadius);

            int segments = 4 + random.nextInt(MAX_BOLT_SEGMENTS - 3);
            boltSegments[b] = segments;
            for (int s = 0; s < segments; s++) {
                boltX[b][s] = cx;
                boltY[b][s] = cy;
                boltZ[b][s] = cz;
                float stride = 0.24F + random.nextFloat() * 0.32F;
                cx += tangentX * stride + (random.nextFloat() - 0.5F) * 0.34F;
                cy += (random.nextFloat() - 0.45F) * 0.38F;
                cz += tangentZ * stride + (random.nextFloat() - 0.5F) * 0.34F;
            }
        }
    }

    private float positiveAngleRange() {
        float range = arcEnd - arcStart;
        if (range < 0.0F) {
            range += (float) (Math.PI * 2.0D);
        }
        return range;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / Math.max(edge1 - edge0, 0.0001F), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
