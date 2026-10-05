package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Client-side state machine for the 17.2-second Funeral Nova attack.
 *
 * <p>Gemini originally used this timeline as a kill effect. This port is
 * spawned exclusively by the synchronized Funeral Nova spell entity, so the
 * visuals drive the attack timeline and never wait for a death event.</p>
 */
public final class GeminiKillEffectVisualInstance {
    public static final int STAGE_MAGIC_CIRCLE = 1;
    public static final int STAGE_MAGIC_TOWER = 2;
    public static final int STAGE_BLACK_HOLE = 3;
    public static final int STAGE_ACCRETION = 4;
    public static final int STAGE_COLLAPSE = 5;
    public static final int STAGE_VOID = 6;
    public static final int STAGE_FLASH = 7;
    public static final int STAGE_HYPERNOVA = 8;
    public static final int STAGE_AFTERGLOW = 9;
    public static final int STAGE_FADE_OUT = 10;

    public static final float TOTAL_DURATION_SECONDS = 17.2F;
    public static final int TOTAL_DURATION_TICKS = 344;

    private static final float T_CIRCLE_END = 0.8F;
    private static final float T_TOWER_END = 2.4F;
    private static final float T_HOLE_END = 3.4F;
    private static final float T_ACCRETION_END = 5.0F;
    private static final float T_COLLAPSE_END = 6.0F;
    private static final float T_VOID_END = 7.5F;
    private static final float T_FLASH_END = 8.2F;
    private static final float T_NOVA_END = 12.5F;
    private static final float T_AFTERGLOW_END = 15.0F;
    private static final float TRANSITION_OVERLAP = 0.30F;
    private static final float SIMULATION_STEP = 0.05F;
    private static final int MAX_CATCHUP_STEPS = 8;

    private static final int MAX_PARTICLES = 3000;
    private static final int MAX_BURST = 1800;

    private final float[] particleData = new float[MAX_PARTICLES * 8];
    private final boolean[] particleIsSky = new boolean[MAX_PARTICLES];
    private final float[] burstData = new float[MAX_BURST * 8];
    private final Random random;
    private final int synchronizedSeed;
    private final long initialSeed;

    private Vec3 position;
    private int particleCount;
    private int burstCount;
    private boolean burstSpawned;
    private float simulatedAgeSeconds;
    private float shakeIntensity;

    public GeminiKillEffectVisualInstance(Vec3 position, int synchronizedSeed) {
        this.position = position;
        this.synchronizedSeed = synchronizedSeed;
        long mixedSeed = Integer.toUnsignedLong(synchronizedSeed)
                ^ Double.doubleToLongBits(position.x * 17.0D + position.z * 31.0D)
                ^ Double.doubleToLongBits(position.y * 13.0D);
        this.initialSeed = mixedSeed;
        this.random = new Random(mixedSeed);
    }

    public int synchronizedSeed() {
        return synchronizedSeed;
    }

    public Vec3 position() {
        return position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public int currentStage(float ageSeconds) {
        float t = Math.max(ageSeconds, 0.0F);
        if (t < T_CIRCLE_END) return STAGE_MAGIC_CIRCLE;
        if (t < T_TOWER_END) return STAGE_MAGIC_TOWER;
        if (t < T_HOLE_END) return STAGE_BLACK_HOLE;
        if (t < T_ACCRETION_END) return STAGE_ACCRETION;
        if (t < T_COLLAPSE_END) return STAGE_COLLAPSE;
        if (t < T_VOID_END) return STAGE_VOID;
        if (t < T_FLASH_END) return STAGE_FLASH;
        if (t < T_NOVA_END) return STAGE_HYPERNOVA;
        if (t < T_AFTERGLOW_END) return STAGE_AFTERGLOW;
        if (t < TOTAL_DURATION_SECONDS) return STAGE_FADE_OUT;
        return -1;
    }

    public float stageProgress(float ageSeconds) {
        float t = Math.max(ageSeconds, 0.0F);
        return switch (currentStage(t)) {
            case STAGE_MAGIC_CIRCLE -> t / T_CIRCLE_END;
            case STAGE_MAGIC_TOWER -> (t - T_CIRCLE_END) / (T_TOWER_END - T_CIRCLE_END);
            case STAGE_BLACK_HOLE -> (t - T_TOWER_END) / (T_HOLE_END - T_TOWER_END);
            case STAGE_ACCRETION -> (t - T_HOLE_END) / (T_ACCRETION_END - T_HOLE_END);
            case STAGE_COLLAPSE -> (t - T_ACCRETION_END) / (T_COLLAPSE_END - T_ACCRETION_END);
            case STAGE_VOID -> (t - T_COLLAPSE_END) / (T_VOID_END - T_COLLAPSE_END);
            case STAGE_FLASH -> (t - T_VOID_END) / (T_FLASH_END - T_VOID_END);
            case STAGE_HYPERNOVA -> (t - T_FLASH_END) / (T_NOVA_END - T_FLASH_END);
            case STAGE_AFTERGLOW -> (t - T_NOVA_END) / (T_AFTERGLOW_END - T_NOVA_END);
            case STAGE_FADE_OUT -> (t - T_AFTERGLOW_END) / (TOTAL_DURATION_SECONDS - T_AFTERGLOW_END);
            default -> 0.0F;
        };
    }

    public boolean isAlive(float ageSeconds) {
        return currentStage(ageSeconds) >= 0;
    }

    public boolean shouldRenderMagic(float ageSeconds) {
        int stage = currentStage(ageSeconds);
        return stage == STAGE_MAGIC_CIRCLE
                || stage == STAGE_MAGIC_TOWER
                || (stage == STAGE_BLACK_HOLE && stageProgress(ageSeconds) < TRANSITION_OVERLAP);
    }

    public boolean shouldRenderBlackHole(float ageSeconds) {
        int stage = currentStage(ageSeconds);
        return (stage >= STAGE_BLACK_HOLE && stage <= STAGE_COLLAPSE)
                || (stage == STAGE_MAGIC_TOWER
                && stageProgress(ageSeconds) > 1.0F - TRANSITION_OVERLAP);
    }

    public float magicTransitionAlpha(float ageSeconds) {
        int stage = currentStage(ageSeconds);
        float progress = stageProgress(ageSeconds);
        if (stage == STAGE_MAGIC_TOWER) {
            if (progress > 1.0F - TRANSITION_OVERLAP) {
                return 1.0F - (progress - (1.0F - TRANSITION_OVERLAP)) / TRANSITION_OVERLAP;
            }
            return 1.0F;
        }
        if (stage == STAGE_BLACK_HOLE && progress < TRANSITION_OVERLAP) {
            return clamp01(1.0F
                    - (1.0F - TRANSITION_OVERLAP + progress) / (1.0F + TRANSITION_OVERLAP));
        }
        return stage == STAGE_MAGIC_CIRCLE ? 1.0F : 0.0F;
    }

    public float blackHoleTransitionAlpha(float ageSeconds) {
        int stage = currentStage(ageSeconds);
        float progress = stageProgress(ageSeconds);
        if (stage == STAGE_MAGIC_TOWER && progress > 1.0F - TRANSITION_OVERLAP) {
            return ((progress - (1.0F - TRANSITION_OVERLAP)) / TRANSITION_OVERLAP) * 0.35F;
        }
        if (stage == STAGE_BLACK_HOLE) {
            if (progress < TRANSITION_OVERLAP) {
                return 0.35F + 0.65F * progress / TRANSITION_OVERLAP;
            }
            return 1.0F;
        }
        if (stage == STAGE_ACCRETION) return 1.0F;
        if (stage == STAGE_COLLAPSE) return transitionAlpha(ageSeconds, false, true);
        return 0.0F;
    }

    public float transitionAlpha(float ageSeconds, boolean fadeIn, boolean fadeOut) {
        float progress = stageProgress(ageSeconds);
        float alpha = 1.0F;
        if (fadeIn && progress < 0.2F) {
            alpha = 1.0F - (float) Math.pow(2.0D, -10.0D * progress / 0.2F);
        }
        if (fadeOut && progress > 0.8F) {
            alpha = (float) Math.pow(2.0D, 10.0D * ((progress - 0.8F) / 0.2F - 1.0F));
        }
        return clamp01(alpha);
    }

    public float fadeOutAlpha(float ageSeconds) {
        if (currentStage(ageSeconds) != STAGE_FADE_OUT) return 1.0F;
        float t = stageProgress(ageSeconds);
        return 1.0F - t * t * (3.0F - 2.0F * t);
    }

    public float chainFade(float ageSeconds) {
        float entry = smoothstep01(ageSeconds / 0.9F);
        return currentStage(ageSeconds) == STAGE_FADE_OUT
                ? entry * fadeOutAlpha(ageSeconds)
                : entry;
    }

    /** Advances the deterministic 20 TPS particle simulation to the entity age. */
    public void advanceTo(float ageSeconds) {
        if (!Float.isFinite(ageSeconds)) return;
        float target = Math.min(Math.max(ageSeconds, 0.0F), TOTAL_DURATION_SECONDS);
        if (target - simulatedAgeSeconds > (MAX_CATCHUP_STEPS + 1) * SIMULATION_STEP) {
            float start = Math.max(0.0F, (float) Math.floor(target / SIMULATION_STEP)
                    * SIMULATION_STEP - MAX_CATCHUP_STEPS * SIMULATION_STEP);
            restoreDecorativeState(start);
        }
        int steps = 0;
        while (steps++ < MAX_CATCHUP_STEPS
                && simulatedAgeSeconds + SIMULATION_STEP <= target + 1.0E-5F) {
            float stepAge = simulatedAgeSeconds + SIMULATION_STEP;
            int stage = currentStage(stepAge);
            updateShake(stage, stepAge);
            if (stage >= STAGE_MAGIC_CIRCLE && stage <= STAGE_COLLAPSE) {
                updateParticles(SIMULATION_STEP, stage, stepAge);
            }
            if (stage >= STAGE_FLASH && stage <= STAGE_AFTERGLOW) {
                updateBurstParticles(SIMULATION_STEP, stage, stepAge);
            }
            simulatedAgeSeconds = stepAge;
        }
    }

    /** Restore a cosmetic snapshot after an unseen interval; never replay old combat time. */
    private void restoreDecorativeState(float age) {
        simulatedAgeSeconds = age;
        particleCount = 0;
        burstCount = 0;
        burstSpawned = false;
        shakeIntensity = 0.0F;
        random.setSeed(initialSeed ^ (long) Math.round(age / SIMULATION_STEP) * 0x9E3779B97F4A7C15L);
        int stage = currentStage(age);
        float quality = AdaptiveVisualQuality.decorationScale();
        if (stage >= STAGE_MAGIC_CIRCLE && stage <= STAGE_COLLAPSE) {
            particleCount = Math.min(MAX_PARTICLES,
                    Math.max(0, Math.round(Math.min(age, T_TOWER_END) / SIMULATION_STEP * 30 * quality)));
            for (int index = 0; index < particleCount; index++) {
                if (stage <= STAGE_MAGIC_TOWER) spawnSkyParticle(index);
                else respawnAccretionParticle(index);
            }
        }
        float burstTime = T_VOID_END + (T_FLASH_END - T_VOID_END) * 0.30F;
        if (age >= burstTime && stage <= STAGE_AFTERGLOW) {
            spawnBurst();
            int elapsedSteps = Math.max(0, (int) Math.floor((age - burstTime) / SIMULATION_STEP));
            float drag = (float) Math.exp(-1.15D * SIMULATION_STEP);
            float decay = (float) Math.pow(drag, elapsedSteps);
            float sum = drag * (1.0F - decay) / (1.0F - drag);
            float gravity = 1.65F * SIMULATION_STEP;
            for (int index = 0; index < burstCount; index++) {
                int at = index * 8;
                burstData[at] += SIMULATION_STEP * burstData[at + 3] * sum;
                burstData[at + 1] += SIMULATION_STEP * (burstData[at + 4] * sum
                        - gravity / (1.0F - drag) * (elapsedSteps - sum));
                burstData[at + 2] += SIMULATION_STEP * burstData[at + 5] * sum;
                burstData[at + 3] *= decay;
                burstData[at + 4] = burstData[at + 4] * decay
                        - gravity * (1.0F - decay) / (1.0F - drag);
                burstData[at + 5] *= decay;
                burstData[at + 6] = elapsedSteps * SIMULATION_STEP;
            }
        }
        updateShake(stage, age);
    }

    public int particleCount() {
        return particleCount;
    }

    public int burstCount() {
        return burstCount;
    }

    public float shakeIntensity() {
        return shakeIntensity;
    }

    private void updateShake(int stage, float ageSeconds) {
        shakeIntensity *= 0.9F;
        if (shakeIntensity < 0.001F) {
            shakeIntensity = 0.0F;
        }

        float progress = stageProgress(ageSeconds);
        float stageShake = 0.0F;
        if (stage == STAGE_COLLAPSE) {
            stageShake = progress * 4.0F;
        } else if (stage == STAGE_VOID) {
            float decay = 1.0F - progress;
            stageShake = 4.0F * decay * decay;
        } else if (stage == STAGE_FLASH) {
            float offset = progress - 0.4F;
            float divisor = progress < 0.4F ? 0.04F : 0.12F;
            stageShake = 8.5F * (float) Math.exp(-(offset * offset) / divisor);
        } else if (stage == STAGE_HYPERNOVA) {
            float attack = Math.min(progress / 0.05F, 1.0F);
            attack = attack * attack * (3.0F - 2.0F * attack);
            float sustain = 1.0F - progress * 0.62F;
            float pulse = 0.82F + 0.18F
                    * Math.abs((float) Math.sin(progress * Math.PI * 7.0F));
            stageShake = sustain * pulse * 7.5F * attack;
        }
        shakeIntensity = Math.max(shakeIntensity, stageShake);
    }

    public int fillParticleBatch(float[] batch, int maxCount, float alpha) {
        int count = 0;
        for (int i = 0; i < particleCount && count < maxCount; i++) {
            int offset = i * 8;
            float life = particleData[offset + 6];
            float maxLife = particleData[offset + 7];
            if (life >= maxLife) continue;
            float ratio = life / maxLife;
            float particleAlpha = alpha * (1.0F - ratio)
                    * (ratio < 0.1F ? ratio / 0.1F : 1.0F);
            int target = count * 8;
            batch[target] = particleData[offset];
            batch[target + 1] = particleData[offset + 1];
            batch[target + 2] = particleData[offset + 2];
            batch[target + 3] = (particleIsSky[i] ? 0.06F : 0.045F)
                    * (1.0F - ratio * (particleIsSky[i] ? 0.4F : 0.55F));
            batch[target + 4] = particleIsSky[i] ? ratio * 0.3F : ratio;
            batch[target + 5] = particleIsSky[i] ? 1.0F : 0.0F;
            batch[target + 6] = 0.0F;
            batch[target + 7] = particleAlpha;
            count++;
        }
        return count;
    }

    public int fillBurstBatch(float[] batch, int maxCount, float alpha) {
        int count = 0;
        for (int i = 0; i < burstCount && count < maxCount; i++) {
            int offset = i * 8;
            float life = burstData[offset + 6];
            float maxLife = burstData[offset + 7];
            if (life >= maxLife) continue;
            float ratio = life / maxLife;
            float decay = 1.0F - ratio;
            float particleAlpha = Math.min(alpha * decay * decay, 1.0F);
            if (particleAlpha < 0.004F) continue;
            int target = count * 8;
            batch[target] = burstData[offset];
            batch[target + 1] = burstData[offset + 1];
            batch[target + 2] = burstData[offset + 2];
            batch[target + 3] = 0.055F * (1.0F + ratio * 1.6F);
            batch[target + 4] = ratio;
            batch[target + 5] = 0.5F;
            batch[target + 6] = 0.0F;
            batch[target + 7] = particleAlpha;
            count++;
        }
        return count;
    }

    private void spawnSkyParticle(int index) {
        int offset = index * 8;
        double theta = random.nextDouble() * Math.PI * 2.0D;
        double radius = 3.0D + random.nextDouble() * 16.0D;
        particleData[offset] = (float) (position.x + Math.cos(theta) * radius);
        particleData[offset + 1] = (float) position.y + 8.0F + random.nextFloat() * 25.0F;
        particleData[offset + 2] = (float) (position.z + Math.sin(theta) * radius);
        particleData[offset + 3] = (random.nextFloat() - 0.5F) * 0.6F;
        particleData[offset + 4] = (random.nextFloat() - 0.5F) * 0.3F;
        particleData[offset + 5] = (random.nextFloat() - 0.5F) * 0.6F;
        particleData[offset + 6] = 0.0F;
        particleData[offset + 7] = 2.0F + random.nextFloat() * 4.0F;
        particleIsSky[index] = true;
    }

    private void respawnAccretionParticle(int index) {
        int offset = index * 8;
        double theta = random.nextDouble() * Math.PI * 2.0D;
        float layer = random.nextFloat();
        double radius = layer < 0.42F
                ? 0.75D + random.nextDouble() * 1.55D
                : layer < 0.82F
                ? 1.8D + random.nextDouble() * 3.8D
                : 5.0D + random.nextDouble() * 4.5D;
        float thickness = 0.08F + (float) radius * 0.028F;
        float yOffset = (random.nextFloat() - 0.5F) * 2.0F * thickness;
        float centerY = (float) position.y + 1.5F;
        particleData[offset] = (float) (position.x + Math.cos(theta) * radius);
        particleData[offset + 1] = centerY + yOffset;
        particleData[offset + 2] = (float) (position.z + Math.sin(theta) * radius);
        double orbitalSpeed = 5.2D / Math.sqrt(Math.max(radius, 0.35D));
        double radialDrift = -0.32D / Math.max(radius, 0.45D);
        particleData[offset + 3] = (float) (-Math.sin(theta) * orbitalSpeed
                + Math.cos(theta) * radialDrift);
        particleData[offset + 4] = (float) (random.nextDouble() * 0.2D - 0.1D);
        particleData[offset + 5] = (float) (Math.cos(theta) * orbitalSpeed
                + Math.sin(theta) * radialDrift);
        particleData[offset + 6] = 0.0F;
        particleData[offset + 7] = 2.2F + random.nextFloat() * 3.2F;
        particleIsSky[index] = false;
    }

    private void updateParticles(float dt, int stage, float ageSeconds) {
        float centerX = (float) position.x;
        float centerY = (float) position.y + 1.5F;
        float centerZ = (float) position.z;
        boolean preBlackHole = stage == STAGE_MAGIC_CIRCLE || stage == STAGE_MAGIC_TOWER;
        boolean blackHole = stage >= STAGE_BLACK_HOLE && stage <= STAGE_COLLAPSE;
        if (preBlackHole && particleCount < MAX_PARTICLES) {
            int spawnCount = Math.max(1, Math.round(30 * AdaptiveVisualQuality.decorationScale()));
            for (int i = 0; i < spawnCount && particleCount < MAX_PARTICLES; i++) {
                spawnSkyParticle(particleCount++);
            }
        }

        float acceleration;
        float speedCap;
        if (stage == STAGE_BLACK_HOLE) {
            float progress = stageProgress(ageSeconds);
            acceleration = 15.0F + progress * 60.0F;
            speedCap = 10.0F + progress * 40.0F;
        } else if (stage == STAGE_ACCRETION) {
            acceleration = 80.0F;
            speedCap = 55.0F;
        } else {
            acceleration = 150.0F;
            speedCap = 80.0F;
        }

        for (int i = 0; i < particleCount; i++) {
            int offset = i * 8;
            float life = particleData[offset + 6] + dt;
            particleData[offset + 6] = life;
            if (life >= particleData[offset + 7]) {
                if (blackHole) respawnAccretionParticle(i);
                else if (preBlackHole) spawnSkyParticle(i);
                continue;
            }
            if (preBlackHole) {
                particleData[offset] += Math.sin(i * 1.7F + life * 0.8F) * 0.15F * dt;
                particleData[offset + 1] += Math.cos(i * 2.3F + life * 0.6F) * 0.08F * dt;
                particleData[offset + 2] += Math.cos(i * 1.9F + life * 0.7F) * 0.15F * dt;
                continue;
            }
            if (!blackHole) continue;

            float dx = centerX - particleData[offset];
            float dy = centerY - particleData[offset + 1];
            float dz = centerZ - particleData[offset + 2];
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 0.01F) {
                particleData[offset + 6] = particleData[offset + 7];
                continue;
            }
            float inverseDistance = 1.0F / distance;
            float directionX = dx * inverseDistance;
            float directionY = dy * inverseDistance;
            float directionZ = dz * inverseDistance;
            float accelerationMagnitude = acceleration
                    * (0.5F + 0.5F * clamp01(distance / 8.0F));
            float targetSpeed = Math.min(speedCap, accelerationMagnitude * distance * 0.3F);
            float blend = clamp(acceleration * dt * 0.15F, 0.01F, 0.3F);
            float targetX;
            float targetY;
            float targetZ;
            if (!particleIsSky[i] && stage != STAGE_COLLAPSE) {
                float horizontalRadius = (float) Math.sqrt(dx * dx + dz * dz);
                float inverseHorizontal = 1.0F / Math.max(horizontalRadius, 0.05F);
                float radialX = -dx * inverseHorizontal;
                float radialZ = -dz * inverseHorizontal;
                float tangentX = -radialZ;
                float tangentZ = radialX;
                float orbitSpeed = 5.8F / (float) Math.sqrt(Math.max(horizontalRadius, 0.4F));
                float inwardSpeed = 0.45F + 0.65F / Math.max(horizontalRadius, 0.6F);
                targetX = tangentX * orbitSpeed - radialX * inwardSpeed;
                targetY = dy * 2.8F;
                targetZ = tangentZ * orbitSpeed - radialZ * inwardSpeed;
                blend = clamp(dt * 3.8F, 0.04F, 0.22F);
            } else {
                targetX = directionX * targetSpeed;
                targetY = directionY * targetSpeed;
                targetZ = directionZ * targetSpeed;
            }
            particleData[offset + 3] += (targetX - particleData[offset + 3]) * blend;
            particleData[offset + 4] += (targetY - particleData[offset + 4]) * blend;
            particleData[offset + 5] += (targetZ - particleData[offset + 5]) * blend;
            particleData[offset] += particleData[offset + 3] * dt;
            particleData[offset + 1] += particleData[offset + 4] * dt;
            particleData[offset + 2] += particleData[offset + 5] * dt;
            float remainingX = particleData[offset] - centerX;
            float remainingY = particleData[offset + 1] - centerY;
            float remainingZ = particleData[offset + 2] - centerZ;
            if (remainingX * remainingX + remainingY * remainingY + remainingZ * remainingZ < 0.1444F) {
                particleData[offset + 6] = particleData[offset + 7];
            }
        }
    }

    private void updateBurstParticles(float dt, int stage, float ageSeconds) {
        if (!burstSpawned && stage == STAGE_FLASH && stageProgress(ageSeconds) >= 0.30F) {
            spawnBurst();
        }
        if (burstCount == 0) return;
        float drag = (float) Math.exp(-1.15D * dt);
        float gravity = 1.65F * dt;
        for (int i = 0; i < burstCount; i++) {
            int offset = i * 8;
            float life = burstData[offset + 6] + dt;
            burstData[offset + 6] = life;
            if (life >= burstData[offset + 7]) continue;
            burstData[offset + 3] *= drag;
            burstData[offset + 4] = burstData[offset + 4] * drag - gravity;
            burstData[offset + 5] *= drag;
            burstData[offset] += burstData[offset + 3] * dt;
            burstData[offset + 1] += burstData[offset + 4] * dt;
            burstData[offset + 2] += burstData[offset + 5] * dt;
        }
    }

    private void spawnBurst() {
        burstSpawned = true;
        int count = Math.min(MAX_BURST, Math.max(1,
                Math.round(1270 * AdaptiveVisualQuality.decorationScale())));
        float centerX = (float) position.x;
        float centerY = (float) position.y + 1.6F;
        float centerZ = (float) position.z;
        for (int i = 0; i < count; i++) {
            int offset = i * 8;
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double cosPhi = random.nextDouble() * 2.0D - 1.0D;
            double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
            float dx = (float) (sinPhi * Math.cos(theta));
            float dy = (float) (cosPhi * 0.75D + 0.30D);
            float dz = (float) (sinPhi * Math.sin(theta));
            float speed = 6.5F + random.nextFloat() * random.nextFloat() * 31.0F;
            burstData[offset] = centerX + dx * 0.3F;
            burstData[offset + 1] = centerY + dy * 0.3F;
            burstData[offset + 2] = centerZ + dz * 0.3F;
            burstData[offset + 3] = dx * speed;
            burstData[offset + 4] = dy * speed;
            burstData[offset + 5] = dz * speed;
            burstData[offset + 6] = 0.0F;
            burstData[offset + 7] = 3.4F + random.nextFloat() * 2.4F;
        }
        burstCount = count;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float smoothstep01(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }
}
