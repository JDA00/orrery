package com.jda.orrery.core.frames;

/**
 * IAU rotation parameters for celestial bodies.
 *
 * Based on IAU Working Group reports and NASA SPICE PCK data. These parameters define the
 * orientation of a body-fixed frame relative to J2000.
 *
 * The rotation model follows the IAU convention: - α = α0 + α1*T : Right ascension of the north
 * pole - δ = δ0 + δ1*T : Declination of the north pole - W = W0 + W1*d : Location of the prime
 * meridian
 *
 * Where: - T = centuries since J2000 epoch - d = days since J2000 epoch (including fractional
 * part)
 *
 * Reference: IAU Working Group on Cartographic Coordinates and Rotational Elements
 * https://astrogeology.usgs.gov/groups/iau-wgccre
 *
 * Values from: NASA SPICE PCK kernel pck00010.tpc
 */
public class IAURotationParameters {

    // Pole orientation (degrees)
    public final double poleRA; // α0: Right ascension at J2000
    public final double poleRARate; // α1: RA change per century (deg/century)
    public final double poleDec; // δ0: Declination at J2000
    public final double poleDecRate; // δ1: Dec change per century (deg/century)

    // Prime meridian (degrees)
    public final double meridianW0; // W0: Prime meridian at J2000
    public final double meridianRate; // W1: Rotation rate (deg/day)

    // Body identifier
    public final String bodyId;
    public final String name;

    /** Constructor for bodies with simple rotation models. */
    public IAURotationParameters(
            String bodyId,
            String name,
            double poleRA,
            double poleRARate,
            double poleDec,
            double poleDecRate,
            double meridianW0,
            double meridianRate) {
        this.bodyId = bodyId;
        this.name = name;
        this.poleRA = poleRA;
        this.poleRARate = poleRARate;
        this.poleDec = poleDec;
        this.poleDecRate = poleDecRate;
        this.meridianW0 = meridianW0;
        this.meridianRate = meridianRate;
    }

    /** Constructor for bodies with no precession (most common case). */
    public IAURotationParameters(
            String bodyId,
            String name,
            double poleRA,
            double poleDec,
            double meridianW0,
            double meridianRate) {
        this(bodyId, name, poleRA, 0.0, poleDec, 0.0, meridianW0, meridianRate);
    }

    /**
     * Calculate pole right ascension at given time.
     *
     * @param centuriesSinceJ2000 Centuries since J2000 (TDB)
     */
    public double getPoleRA(double centuriesSinceJ2000) {
        return poleRA + poleRARate * centuriesSinceJ2000;
    }

    /**
     * Calculate pole declination at given time.
     *
     * @param centuriesSinceJ2000 Centuries since J2000 (TDB)
     */
    public double getPoleDec(double centuriesSinceJ2000) {
        return poleDec + poleDecRate * centuriesSinceJ2000;
    }

    /**
     * Calculate prime meridian angle at given time.
     *
     * @param daysSinceJ2000 Days since J2000 (TDB)
     */
    public double getPrimeMeridian(double daysSinceJ2000) {
        return meridianW0 + meridianRate * daysSinceJ2000;
    }

    // Pre-defined IAU rotation parameters.
    // Values from NASA SPICE PCK kernel pck00010.tpc (2009 IAU report).

    // Sun (NAIF ID: 10)
    public static final IAURotationParameters SUN =
            new IAURotationParameters(
                    "sun",
                    "Sun",
                    286.13,
                    0.0, // Pole RA (no precession)
                    63.87,
                    0.0, // Pole Dec (no precession)
                    84.176,
                    14.1844 // Prime meridian (Carrington rotation)
                    );

    // Mercury (NAIF ID: 199)
    public static final IAURotationParameters MERCURY =
            new IAURotationParameters(
                    "mercury",
                    "Mercury",
                    281.0103,
                    -0.0328, // Pole RA with precession
                    61.4155,
                    -0.0049, // Pole Dec with precession
                    329.5988,
                    6.1385108 // Prime meridian (3:2 spin-orbit resonance)
                    );

    // Venus (NAIF ID: 299)
    public static final IAURotationParameters VENUS =
            new IAURotationParameters(
                    "venus",
                    "Venus",
                    272.76,
                    0.0, // Pole RA
                    67.16,
                    0.0, // Pole Dec
                    160.20,
                    -1.4813688 // Prime meridian (retrograde rotation)
                    );

    // Earth (NAIF ID: 399)
    // Note: For high-precision work, use ITRF93 instead of IAU_EARTH
    public static final IAURotationParameters EARTH =
            new IAURotationParameters(
                    "earth",
                    "Earth",
                    0.0,
                    -0.641, // Pole RA with precession
                    90.0,
                    -0.557, // Pole Dec with precession
                    190.147,
                    360.9856235 // Prime meridian (sidereal rotation)
                    );

    // Mars (NAIF ID: 499)
    public static final IAURotationParameters MARS =
            new IAURotationParameters(
                    "mars",
                    "Mars",
                    317.68143,
                    -0.1061, // Pole RA with precession
                    52.88650,
                    -0.0609, // Pole Dec with precession
                    176.630,
                    350.89198226 // Prime meridian
                    );

    // Jupiter (NAIF ID: 599)
    // System III (radio rotation)
    public static final IAURotationParameters JUPITER =
            new IAURotationParameters(
                    "jupiter",
                    "Jupiter",
                    268.056595,
                    -0.006499, // Pole RA with precession
                    64.495303,
                    0.002413, // Pole Dec with precession
                    284.95,
                    870.5360000 // Prime meridian (System III)
                    );

    // Saturn (NAIF ID: 699)
    // System III (radio rotation)
    public static final IAURotationParameters SATURN =
            new IAURotationParameters(
                    "saturn",
                    "Saturn",
                    40.589,
                    -0.036, // Pole RA with precession
                    83.537,
                    -0.004, // Pole Dec with precession
                    38.90,
                    810.7939024 // Prime meridian (System III)
                    );

    // Uranus (NAIF ID: 799)
    public static final IAURotationParameters URANUS =
            new IAURotationParameters(
                    "uranus",
                    "Uranus",
                    257.311,
                    0.0, // Pole RA
                    -15.175,
                    0.0, // Pole Dec (unusual tilt)
                    203.81,
                    -501.1600928 // Prime meridian (retrograde)
                    );

    // Neptune (NAIF ID: 899)
    public static final IAURotationParameters NEPTUNE =
            new IAURotationParameters(
                    "neptune",
                    "Neptune",
                    299.36,
                    0.70, // Pole RA with precession
                    43.46,
                    -0.51, // Pole Dec with precession
                    253.18,
                    536.3128492 // Prime meridian
                    );

    // Moon (NAIF ID: 301)
    // Note: Full model includes libration terms
    public static final IAURotationParameters MOON =
            new IAURotationParameters(
                    "moon",
                    "Moon",
                    269.9949,
                    0.0031, // Pole RA with precession
                    66.5392,
                    0.0130, // Pole Dec with precession
                    38.3213,
                    13.17635815 // Prime meridian (tidally locked + libration)
                    );

    /** Get rotation parameters for a body by ID. */
    public static IAURotationParameters forBody(String bodyId) {
        switch (bodyId.toLowerCase()) {
            case "sun":
                return SUN;
            case "mercury":
                return MERCURY;
            case "venus":
                return VENUS;
            case "earth":
                return EARTH;
            case "mars":
                return MARS;
            case "jupiter":
                return JUPITER;
            case "saturn":
                return SATURN;
            case "uranus":
                return URANUS;
            case "neptune":
                return NEPTUNE;
            case "moon":
                return MOON;
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "IAURotation[%s: pole=(%.2f°,%.2f°), W0=%.2f°, rate=%.4f°/day]",
                name, poleRA, poleDec, meridianW0, meridianRate);
    }
}
