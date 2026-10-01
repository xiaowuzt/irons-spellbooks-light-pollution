package com.gang.lightpollution.text.world;

/**
 * How one floating text behaves: how big, how long, and how it enters and leaves.
 *
 * <p>Ported from ArcaneVortex's {@code WorldTextConfig} with the original author's permission;
 * see {@code CREDITS.txt}. Builder-style so a call site reads as one expression.</p>
 *
 * <p>Durations are in ticks. Zero is allowed for any of the three phases — {@code setNoStay} exists
 * for exactly that.</p>
 */
public final class WorldTextConfig {
    /**
     * The entry and exit curves.
     *
     * <p>The whole catalogue is carried rather than only the ones this mod currently uses, because
     * the point of the port is having them to pick from.</p>
     */
    public enum AnimationType {
        NONE,
        FADE_IN, FADE_OUT,
        SCALE_IN, SCALE_OUT,
        SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT,
        BOUNCE_IN, BOUNCE_OUT,
        ELASTIC_IN, ELASTIC_OUT,
        ZOOM_IN, ZOOM_OUT,
        ROTATE_IN, ROTATE_OUT,
        FLIP_IN, FLIP_OUT
    }

    private float scale = 1.0F;
    private boolean shadow = true;
    private boolean background;
    private int backgroundColour = 0x80000000;
    private AnimationType enterAnimation = AnimationType.FADE_IN;
    private AnimationType exitAnimation = AnimationType.FADE_OUT;
    private long enterDuration = 10L;
    private long stayDuration = 40L;
    private long exitDuration = 10L;
    private boolean billboard = true;
    private float yOffset;
    private float rotationAngle;
    private boolean rotationAnimated;
    private long rotationDuration = 10L;
    private boolean rotationRandomDirection;
    private float rotationMinAngle;
    private float rotationMaxAngle;
    private float randomOffsetX;
    private float randomOffsetY;
    private float randomOffsetZ;
    private boolean randomOffsetEnabled;

    public static WorldTextConfig createDefault() {
        return new WorldTextConfig();
    }

    public WorldTextConfig setScale(float value) {
        this.scale = value;
        return this;
    }

    public WorldTextConfig setShadow(boolean value) {
        this.shadow = value;
        return this;
    }

    public WorldTextConfig setBackground(boolean value) {
        this.background = value;
        return this;
    }

    public WorldTextConfig setBackgroundColour(int argb) {
        this.backgroundColour = argb;
        this.background = true;
        return this;
    }

    public WorldTextConfig setEnterAnimation(AnimationType value) {
        this.enterAnimation = value;
        return this;
    }

    public WorldTextConfig setExitAnimation(AnimationType value) {
        this.exitAnimation = value;
        return this;
    }

    public WorldTextConfig setEnterDuration(long ticks) {
        this.enterDuration = ticks;
        return this;
    }

    public WorldTextConfig setStayDuration(long ticks) {
        this.stayDuration = ticks;
        return this;
    }

    public WorldTextConfig setExitDuration(long ticks) {
        this.exitDuration = ticks;
        return this;
    }

    /** Enter straight into exit, for something that should only flash. */
    public WorldTextConfig setNoStay() {
        this.stayDuration = 0L;
        return this;
    }

    public WorldTextConfig setBillboard(boolean value) {
        this.billboard = value;
        return this;
    }

    public WorldTextConfig setYOffset(float value) {
        this.yOffset = value;
        return this;
    }

    /** A fixed tilt, in degrees about the view axis. */
    public WorldTextConfig setRotationAngle(float degrees) {
        this.rotationAngle = degrees;
        this.rotationAnimated = false;
        return this;
    }

    /** Turn to {@code degrees} over {@code ticks} rather than starting there. */
    public WorldTextConfig setRotationAnimated(float degrees, long ticks) {
        this.rotationAngle = degrees;
        this.rotationDuration = ticks;
        this.rotationAnimated = true;
        return this;
    }

    public WorldTextConfig setRotationRandomDirection(boolean value) {
        this.rotationRandomDirection = value;
        return this;
    }

    /** Pick the tilt from a range instead of using a single angle. */
    public WorldTextConfig setRotationRandomRange(float minDegrees, float maxDegrees) {
        this.rotationMinAngle = minDegrees;
        this.rotationMaxAngle = maxDegrees;
        return this;
    }

    /**
     * Scatter the spawn position, which is what stops a burst of numbers stacking into an
     * illegible pile.
     */
    public WorldTextConfig setRandomOffset(float x, float y, float z) {
        this.randomOffsetX = x;
        this.randomOffsetY = y;
        this.randomOffsetZ = z;
        this.randomOffsetEnabled = x != 0.0F || y != 0.0F || z != 0.0F;
        return this;
    }

    public WorldTextConfig setRandomOffset(float range) {
        return setRandomOffset(range, range, range);
    }

    public float getScale() {
        return scale;
    }

    public boolean hasShadow() {
        return shadow;
    }

    public boolean hasBackground() {
        return background;
    }

    public int getBackgroundColour() {
        return backgroundColour;
    }

    public AnimationType getEnterAnimation() {
        return enterAnimation;
    }

    public AnimationType getExitAnimation() {
        return exitAnimation;
    }

    public long getEnterDuration() {
        return enterDuration;
    }

    public long getStayDuration() {
        return stayDuration;
    }

    public long getExitDuration() {
        return exitDuration;
    }

    public boolean isBillboard() {
        return billboard;
    }

    public float getYOffset() {
        return yOffset;
    }

    public float getRotationAngle() {
        return rotationAngle;
    }

    public boolean isRotationAnimated() {
        return rotationAnimated;
    }

    public long getRotationDuration() {
        return rotationDuration;
    }

    public boolean isRotationRandomDirection() {
        return rotationRandomDirection;
    }

    public float getRotationMinAngle() {
        return rotationMinAngle;
    }

    public float getRotationMaxAngle() {
        return rotationMaxAngle;
    }

    public float getRandomOffsetX() {
        return randomOffsetX;
    }

    public float getRandomOffsetY() {
        return randomOffsetY;
    }

    public float getRandomOffsetZ() {
        return randomOffsetZ;
    }

    public boolean isRandomOffsetEnabled() {
        return randomOffsetEnabled;
    }
}
