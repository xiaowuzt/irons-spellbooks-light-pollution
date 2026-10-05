package com.gang.lightpollution.fx;

/**
 * Finite shader inputs derived on the CPU from docs/black-hole/references/R1-original.txt.
 * The source model fixes a0 = 0, Rms / Rs = 3 and mu = 1. All intermediate physics uses
 * double precision; only the final uniform values are narrowed to float. Camera position,
 * FOV and the effect's in-game display radius must not change these physical quantities.
 *
 * @param lightSpeedPerRs light speed in Schwarzschild radii per second (c / RsMeters)
 * @param temperatureArgument the original diskA temperature coefficient, in kelvin^4
 * @param peakTemperature4 the original diskA * 0.05665278, in kelvin^4
 * @param rsLightYears the physical Schwarzschild radius in light years
 * @param temporalHalfLife temporal accumulation half-life in unscaled game-time seconds
 */
public record RedshiftDiskParameters(float lightSpeedPerRs, float temperatureArgument,
                                     float peakTemperature4, float rsLightYears,
                                     float temporalHalfLife) {
    private static final double GRAVITY_CONSTANT = 6.673e-11;
    private static final double SPEED_OF_LIGHT = 299792458.0;
    private static final double SOLAR_MASS = 1.9884e30;
    private static final double LIGHT_YEAR = 9460730472580800.0;
    private static final double STEFAN_BOLTZMANN = 5.670373e-8;
    private static final double SOURCE_PI = 3.141592653589;
    private static final double ACCRETION_EFFICIENCY = Math.sqrt(1.0 - 1.0 / 3.0);
    private static final double REFERENCE_RS_LIGHT_YEARS = 0.00000465;
    private static final double MIN_HALF_LIFE = 0.02;
    private static final double MAX_HALF_LIFE = 0.3;

    /** Also guard direct construction, so an invalid float can never become a uniform. */
    public RedshiftDiskParameters {
        requirePositiveFinite(lightSpeedPerRs, "lightSpeedPerRs");
        requirePositiveFinite(temperatureArgument, "temperatureArgument");
        requirePositiveFinite(peakTemperature4, "peakTemperature4");
        requirePositiveFinite(rsLightYears, "rsLightYears");
        requirePositiveFinite(temporalHalfLife, "temporalHalfLife");
        if (temporalHalfLife < (float) MIN_HALF_LIFE || temporalHalfLife > (float) MAX_HALF_LIFE) {
            throw new IllegalArgumentException("temporalHalfLife must be in [0.02, 0.3] seconds");
        }
    }

    /**
     * Converts the original source parameters, without view-dependent rescaling.
     * The R1 defaults are (1.49e7, 2e-6, 30, 1); the 36 in its half-life expression
     * is a reference coefficient, not the default source time rate.
     *
     * @throws IllegalArgumentException for nonfinite inputs, nonpositive mass/accretion,
     *         negative time/rotation rates, or derived uniforms not representable as
     *         positive finite floats
     */
    public static RedshiftDiskParameters from(double massSolar, double accretionRatio,
                                              double sourceTimeRate, double rotationSpeed) {
        requirePositiveFinite(massSolar, "massSolar");
        requirePositiveFinite(accretionRatio, "accretionRatio");
        requireNonNegativeFinite(sourceTimeRate, "sourceTimeRate");
        requireNonNegativeFinite(rotationSpeed, "rotationSpeed");

        double cSquared = SPEED_OF_LIGHT * SPEED_OF_LIGHT;
        double rsMeters = 2.0 * massSolar * GRAVITY_CONSTANT * SOLAR_MASS / cSquared;
        double dmdtEdd = 6.327 / cSquared * massSolar * SOLAR_MASS / ACCRETION_EFFICIENCY;
        double dmdt = accretionRatio * dmdtEdd;
        double diskA = 3.0 * GRAVITY_CONSTANT * SOLAR_MASS
                / (rsMeters * rsMeters * rsMeters) * massSolar * dmdt
                / (8.0 * SOURCE_PI * STEFAN_BOLTZMANN);
        double peak = diskA * 0.05665278;
        double rsLy = rsMeters / LIGHT_YEAR;
        double cPerRs = SPEED_OF_LIGHT / rsMeters;
        double halfLife = halfLife(rsLy, sourceTimeRate, rotationSpeed);

        return new RedshiftDiskParameters((float) cPerRs, (float) diskA, (float) peak,
                (float) rsLy, (float) halfLife);
    }

    private static double halfLife(double rsLy, double timeRate, double rotationSpeed) {
        double effectiveRate = timeRate * rotationSpeed;
        // Includes zero speed and underflow of a product of two positive, finite rates.
        // A stationary disk still uses normal temporal blending; it is not a paused frame.
        if (effectiveRate == 0.0) return MAX_HALF_LIFE;
        if (Double.isInfinite(effectiveRate)) return MIN_HALF_LIFE;

        // R1: omega(r, Rs) = sqrt((c/ly)^2 * Rs / ((2r - 3Rs) * r^2)).
        // At r = 3Rs this is (c/ly) / (sqrt(27) * Rs), hence the exact ratio
        // omega(3 * referenceRs, referenceRs) / omega(3 * rsLy, rsLy) = rsLy / referenceRs.
        // This avoids squaring/cubing light-year radii for the temporal calculation.
        double seconds = 0.131 * 36.0 * (rsLy / REFERENCE_RS_LIGHT_YEARS) / effectiveRate;
        return Math.max(MIN_HALF_LIFE, Math.min(MAX_HALF_LIFE, seconds));
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be nonnegative and finite");
        }
    }
}
