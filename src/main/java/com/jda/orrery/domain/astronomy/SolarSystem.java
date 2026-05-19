package com.jda.orrery.domain.astronomy;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.domain.astronomy.catalog.BodyData;
import com.jda.orrery.domain.astronomy.catalog.CelestialCatalog;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import com.jda.orrery.domain.ephemeris.cache.EphemerisCache;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages the collection of celestial bodies in the solar system.
 *
 * This class builds the solar system from the celestial catalog using a data-driven approach.
 * All body properties are defined in the catalog, and positions are calculated using the analytical
 * ephemeris provider.
 */
public class SolarSystem {
    private static final Logger LOGGER = Logging.logger(SolarSystem.class);

    private final Map<String, CelestialBody> bodies;
    private final Sun sun;
    private final List<Planet> planets;
    private final List<Planet> planetsView;
    private final List<NaturalSatellite> satellites;
    private final List<NaturalSatellite> satellitesView;

    private final EphemerisProvider ephemeris;
    private final EphemerisCache cache;

    /**
     * Create a new solar system with all cataloged bodies.
     *
     * @param ephemeris Ephemeris provider for position calculations
     * @param cache Ephemeris cache shared by all bodies
     */
    public SolarSystem(EphemerisProvider ephemeris, EphemerisCache cache) {
        this.bodies = new HashMap<>();
        this.planets = new ArrayList<>();
        this.planetsView = Collections.unmodifiableList(planets);
        this.satellites = new ArrayList<>();
        this.satellitesView = Collections.unmodifiableList(satellites);

        this.ephemeris = ephemeris;
        this.cache = cache;

        // Build from catalog
        this.sun = createSun();
        buildPlanets();
        buildSatellites();

        LOGGER.info(
                String.format(
                        "Solar system initialized with %d bodies: Sun, %d planets, %d satellites",
                        bodies.size(), planets.size(), satellites.size()));
    }

    /** Create the Sun from catalog data. */
    private Sun createSun() {
        BodyData sunData = CelestialCatalog.SUN;
        Sun sun = new Sun(sunData, ephemeris, cache);
        bodies.put(sunData.getId(), sun);
        return sun;
    }

    /** Build all planets from the catalog. */
    private void buildPlanets() {
        for (BodyData planetData : CelestialCatalog.getPlanets()) {
            Planet planet = new Planet(planetData, ephemeris, cache);
            planets.add(planet);
            bodies.put(planetData.getId(), planet);

            // Planets orbit the Sun
            sun.addChild(planet);

            LOGGER.fine("Added planet: " + planet.getName());
        }
    }

    /**
     * Build satellites and attach them to their parent bodies. Currently only builds bodies
     * supported by the analytical ephemeris.
     */
    private void buildSatellites() {
        // For now, only add the Moon since it's the only satellite
        // supported by our analytical ephemeris (ELP82)
        BodyData moonData = CelestialCatalog.MOON;

        // Find Earth
        CelestialBody earth = bodies.get("earth");
        if (earth == null) {
            LOGGER.warning("Cannot add Moon: Earth not found");
            return;
        }

        // Create and attach Moon to Earth
        NaturalSatellite moon = new NaturalSatellite(moonData, ephemeris, cache);
        satellites.add(moon);
        bodies.put(moonData.getId(), moon);
        bodies.put("moon", moon); // Also add by name for convenience
        earth.addChild(moon);

        LOGGER.info("Added Moon as satellite of Earth");
    }

    /** Get the Sun. */
    public Sun getSun() {
        return sun;
    }

    /**
     * Get all planets in order from the Sun. Returns a pre-allocated unmodifiable view; the
     * underlying list is populated once at construction, so the view remains stable.
     */
    public List<Planet> getPlanets() {
        return planetsView;
    }

    /** Get all natural satellites. Returns a pre-allocated unmodifiable view. */
    public List<NaturalSatellite> getSatellites() {
        return satellitesView;
    }

    @Override
    public String toString() {
        return String.format(
                "SolarSystem[%d bodies, ephemeris=%s]", bodies.size(), ephemeris.getName());
    }
}
