package com.jda.orrery.domain.astronomy.catalog;

import com.jda.orrery.domain.astronomy.BodyType;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Catalog of celestial body data.
 *
 * Pure astronomical data only — rendering decisions belong in the visualization layer, not here.
 *
 * NAIF/SPICE ID conventions: - Sun: 10 - Planets: X99 (Mercury=199, Venus=299, Earth=399, etc.)
 * - Natural satellites: XYY (301=Moon, 401=Phobos, 501=Io, etc.) - Asteroids: Number as assigned by
 * MPC
 *
 * Reference: https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/req/naif_ids.html
 */
public class CelestialCatalog {

    // Primary bodies — Sun and major planets

    public static final BodyData SUN =
            BodyData.withFullData(
                    10,
                    "sun",
                    "Sun",
                    BodyType.STAR,
                    1.9885e30, // mass (kg)
                    695700.0, // radius (km)
                    null, // no parent
                    0.0, // at center
                    0.0, // doesn't orbit
                    609.12, // rotation period (hours) - 25.38 days
                    7.25 // axial tilt (degrees)
                    );

    public static final BodyData MERCURY =
            BodyData.withFullData(
                    199,
                    "mercury",
                    "Mercury",
                    BodyType.PLANET,
                    3.3011e23, // mass (kg)
                    2439.7, // radius (km)
                    null,
                    0.38709893, // semi-major axis (AU)
                    87.969, // orbital period (days)
                    1407.6, // rotation period (hours) - 58.65 days
                    0.034 // axial tilt (degrees)
                    );

    public static final BodyData VENUS =
            BodyData.withFullData(
                    299,
                    "venus",
                    "Venus",
                    BodyType.PLANET,
                    4.8675e24,
                    6051.8,
                    null,
                    0.72333199,
                    224.701,
                    -5832.5, // retrograde rotation
                    177.36);

    public static final BodyData EARTH =
            BodyData.withFullData(
                    399,
                    "earth",
                    "Earth",
                    BodyType.PLANET,
                    5.9724e24,
                    6371.0,
                    null,
                    1.00000011,
                    365.256,
                    23.9345,
                    23.4392911);

    public static final BodyData MARS =
            BodyData.withFullData(
                    499,
                    "mars",
                    "Mars",
                    BodyType.PLANET,
                    6.4171e23,
                    3389.5,
                    null,
                    1.52366231,
                    686.980,
                    24.6229,
                    25.19);

    // Gas giants: include polar radius (IAU 2015) for oblate rendering and shadow geometry.
    // Saturn additionally carries ring extent; the visual values 1.1 / 2.2 (in planet-radii
    // units) are an artistic compression of the real D-ring/A-ring extent (1.105 / 2.27).
    public static final BodyData JUPITER =
            BodyData.withFullData(
                    599,
                    "jupiter",
                    "Jupiter",
                    BodyType.PLANET,
                    1.8982e27,
                    69911.0,
                    null,
                    5.20336301,
                    4332.589,
                    9.9250,
                    3.13,
                    66854.0, // polar radius (IAU 2015)
                    null,
                    null // no rendered rings
                    );

    public static final BodyData SATURN =
            BodyData.withFullData(
                    699,
                    "saturn",
                    "Saturn",
                    BodyType.PLANET,
                    5.6834e26,
                    58232.0,
                    null,
                    9.53707032,
                    10759.22,
                    10.656,
                    26.73,
                    54364.0, // polar radius (IAU 2015)
                    1.1, // ring inner radius (planet-radii units, artistic compression)
                    2.2 // ring outer radius
                    );

    public static final BodyData URANUS =
            BodyData.withFullData(
                    799,
                    "uranus",
                    "Uranus",
                    BodyType.PLANET,
                    8.6810e25,
                    25362.0,
                    null,
                    19.19126393,
                    30685.4,
                    -17.24, // retrograde
                    97.77,
                    24973.0, // polar radius (IAU 2015)
                    null,
                    null);

    public static final BodyData NEPTUNE =
            BodyData.withFullData(
                    899,
                    "neptune",
                    "Neptune",
                    BodyType.PLANET,
                    1.0241e26,
                    24622.0,
                    null,
                    30.06896348,
                    60189.0,
                    16.11,
                    28.32,
                    24341.0, // polar radius (IAU 2015)
                    null,
                    null);

    // Natural satellites — major moons

    // Earth's Moon
    public static final BodyData MOON =
            BodyData.withFullData(
                    301,
                    "moon",
                    "Moon",
                    BodyType.NATURAL_SATELLITE,
                    7.342e22,
                    1737.4,
                    "earth",
                    0.00257, // ~384,400 km in AU
                    27.321661, // orbital period (days)
                    655.728, // rotation period (hours) - tidally locked
                    6.687 // axial tilt to ecliptic
                    );

    // Mars' moons
    public static final BodyData PHOBOS =
            new BodyData(
                    401, "phobos", "Phobos", BodyType.NATURAL_SATELLITE, 1.0659e16, 11.267, "mars");

    public static final BodyData DEIMOS =
            new BodyData(
                    402, "deimos", "Deimos", BodyType.NATURAL_SATELLITE, 1.4762e15, 6.2, "mars");

    // Jupiter's Galilean moons
    public static final BodyData IO =
            BodyData.withFullData(
                    501,
                    "io",
                    "Io",
                    BodyType.NATURAL_SATELLITE,
                    8.931938e22,
                    1821.6,
                    "jupiter",
                    0.002819, // ~421,800 km
                    1.769138, // orbital period
                    42.45, // rotation (tidally locked)
                    0.05);

    public static final BodyData EUROPA =
            BodyData.withFullData(
                    502,
                    "europa",
                    "Europa",
                    BodyType.NATURAL_SATELLITE,
                    4.799844e22,
                    1560.8,
                    "jupiter",
                    0.004485,
                    3.551181,
                    85.22,
                    0.47);

    public static final BodyData GANYMEDE =
            BodyData.withFullData(
                    503,
                    "ganymede",
                    "Ganymede",
                    BodyType.NATURAL_SATELLITE,
                    1.4819e23,
                    2634.1,
                    "jupiter",
                    0.007155,
                    7.154553,
                    171.63,
                    0.20);

    public static final BodyData CALLISTO =
            BodyData.withFullData(
                    504,
                    "callisto",
                    "Callisto",
                    BodyType.NATURAL_SATELLITE,
                    1.075938e23,
                    2410.3,
                    "jupiter",
                    0.012585,
                    16.689018,
                    400.54,
                    0.0);

    // Saturn's major moons
    public static final BodyData MIMAS =
            new BodyData(
                    601, "mimas", "Mimas", BodyType.NATURAL_SATELLITE, 3.7493e19, 198.2, "saturn");

    public static final BodyData ENCELADUS =
            new BodyData(
                    602,
                    "enceladus",
                    "Enceladus",
                    BodyType.NATURAL_SATELLITE,
                    1.0802e20,
                    252.1,
                    "saturn");

    public static final BodyData TITAN =
            BodyData.withFullData(
                    606,
                    "titan",
                    "Titan",
                    BodyType.NATURAL_SATELLITE,
                    1.3452e23,
                    2574.7,
                    "saturn",
                    0.008168,
                    15.945,
                    382.68,
                    0.0);

    // Dwarf planets

    public static final BodyData PLUTO =
            BodyData.withFullData(
                    999,
                    "pluto",
                    "Pluto",
                    BodyType.DWARF_PLANET,
                    1.303e22,
                    1188.3,
                    null,
                    39.48,
                    90560.0,
                    153.29,
                    122.53);

    public static final BodyData CERES =
            BodyData.withFullData(
                    2000001,
                    "ceres",
                    "Ceres",
                    BodyType.DWARF_PLANET,
                    9.393e20,
                    473.0,
                    null,
                    2.766,
                    1680.0,
                    9.074,
                    4.0);

    // Catalog lookup tables

    private static final Map<String, BodyData> BODIES_BY_CODE = new HashMap<>();
    private static final Map<Integer, BodyData> BODIES_BY_NAIF = new HashMap<>();

    static {
        // Register all bodies
        registerBody(SUN);
        registerBody(MERCURY);
        registerBody(VENUS);
        registerBody(EARTH);
        registerBody(MARS);
        registerBody(JUPITER);
        registerBody(SATURN);
        registerBody(URANUS);
        registerBody(NEPTUNE);

        registerBody(MOON);
        registerBody(PHOBOS);
        registerBody(DEIMOS);
        registerBody(IO);
        registerBody(EUROPA);
        registerBody(GANYMEDE);
        registerBody(CALLISTO);
        registerBody(MIMAS);
        registerBody(ENCELADUS);
        registerBody(TITAN);

        registerBody(PLUTO);
        registerBody(CERES);
    }

    private static void registerBody(BodyData body) {
        BODIES_BY_CODE.put(body.code(), body);
        BODIES_BY_NAIF.put(body.naifId(), body);
    }

    /** Find a body by its code. */
    public static Optional<BodyData> findByCode(String code) {
        return Optional.ofNullable(BODIES_BY_CODE.get(code.toLowerCase()));
    }

    /**
     * Look up a body by code, returning null if not found.
     *
     * Zero-allocation alternative to {@link #findByCode(String)} for the render hot path.
     * Assumes the caller already passes a lowercase code (the code field is canonical lowercase per
     * the catalog's conventions).
     */
    public static BodyData getByCode(String code) {
        return code != null ? BODIES_BY_CODE.get(code) : null;
    }

    /** Find a body by its NAIF ID. */
    public static Optional<BodyData> findByNaifId(int naifId) {
        return Optional.ofNullable(BODIES_BY_NAIF.get(naifId));
    }

    /** Get all cataloged bodies. */
    public static Collection<BodyData> getAllBodies() {
        return Collections.unmodifiableCollection(BODIES_BY_CODE.values());
    }

    /** Get all planets in order from the Sun. */
    public static List<BodyData> getPlanets() {
        return List.of(MERCURY, VENUS, EARTH, MARS, JUPITER, SATURN, URANUS, NEPTUNE);
    }

    /** Get all satellites of a given parent body. */
    public static List<BodyData> getSatellitesOf(String parentCode) {
        return BODIES_BY_CODE.values().stream()
                .filter(body -> parentCode.equalsIgnoreCase(body.parentCode()))
                .sorted(Comparator.comparing(BodyData::naifId))
                .collect(Collectors.toList());
    }

    /** Get all natural satellites. */
    public static List<BodyData> getAllSatellites() {
        return BODIES_BY_CODE.values().stream()
                .filter(body -> body.type() == BodyType.NATURAL_SATELLITE)
                .sorted(Comparator.comparing(BodyData::naifId))
                .collect(Collectors.toList());
    }

    /** Get bodies currently supported by the analytical ephemeris. */
    public static List<BodyData> getAnalyticalBodies() {
        List<BodyData> bodies = new ArrayList<>();
        bodies.add(SUN);
        bodies.addAll(getPlanets());
        bodies.add(MOON);
        return bodies;
    }

    // Private constructor to prevent instantiation
    private CelestialCatalog() {}
}
