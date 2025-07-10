package com.jda.orrery.model;

import com.jda.orrery.view.TubeMeshGenerator;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point3D;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;

import java.util.ArrayList;
import java.util.List;

import com.jda.orrery.vsop87.*;

public class SolarSystem {
    // Constants
    private static final double TIME_SCALE = 3.0;
    private static final int ORBIT_SEGMENTS = 180; // For 3D tubes, this is sufficient
    private static final double ORBIT_TUBE_RADIUS = 0.1; // Radius of the 3D tube

    // Scale factors
    private static final double SIZE_SCALE = 2.0;  // Make all planets 2x bigger
    private static final double MIN_PLANET_SIZE = 0.8;

    // Pre-calculate common values
    private static final double TWO_PI = 2 * Math.PI;
    private static final double SEGMENT_ANGLE = TWO_PI / ORBIT_SEGMENTS;
    private static final double NANO_TO_SECONDS = 1_000_000_000.0;

    private final Group planetSystem;
    private final Group orbitGroup;
    private final Star sun;
    private final List<Planet> planets = new ArrayList<>();
    private final List<CelestialBody> celestialBodies = new ArrayList<>(); // List for all bodies
    private double time = 0;
    private AnimationTimer animationTimer;
    private boolean orbitsVisible = false;

    private double[] planetTimeOffsets = new double[8];

    private static double getCurrentJulianDate() {
        long currentMillis = System.currentTimeMillis();
        double daysSince1970 = currentMillis / 86400000.0;
        return daysSince1970 + 2440587.5;  // JD for Jan 1, 1970 00:00 UTC
    }


    public SolarSystem() {
        planetSystem = new Group();
        orbitGroup = new Group();

        // Cache static orbit group
        orbitGroup.setCache(true);
        orbitGroup.setCacheHint(CacheHint.SPEED);

        // Create the Sun as a Star
        sun = new Star("sun", 20, "sun.png", 7.25);
        planetSystem.getChildren().add(sun.getBodyGroup());
        celestialBodies.add(sun); // Add sun to celestial bodies list

        // Get current positions from VSOP87
        double jd = getCurrentJulianDate();
        double t = (jd - 2451545.0) / 36525.0; // Convert to Julian centuries from J2000
        System.out.println("Initializing planets at JD: " + jd);

        // Calculate actual positions from VSOP87
        double[] mercuryPos = vsop87a_small.getMercury(t);
        double[] venusPos = vsop87a_small.getVenus(t);
        double[] earthPos = vsop87a_small.getEarth(t);
        double[] marsPos = vsop87a_small.getMars(t);
        double[] jupiterPos = vsop87a_small.getJupiter(t);
        double[] saturnPos = vsop87a_small.getSaturn(t);
        double[] uranusPos = vsop87a_small.getUranus(t);
        double[] neptunePos = vsop87a_small.getNeptune(t);

        // Log positions for verification
        System.out.printf("Mercury: [%.6f, %.6f, %.6f] AU%n", mercuryPos[0], mercuryPos[1], mercuryPos[2]);
        System.out.printf("Earth: [%.6f, %.6f, %.6f] AU%n", earthPos[0], earthPos[1], earthPos[2]);
        System.out.printf("Jupiter: [%.6f, %.6f, %.6f] AU%n", jupiterPos[0], jupiterPos[1], jupiterPos[2]);

        // Calculate mean distances for orbit sizing (average of semi-major axis)
        // These are the canonical mean distances in AU
        double mercuryMeanAU = 0.387;
        double venusMeanAU = 0.723;
        double earthMeanAU = 1.000;
        double marsMeanAU = 1.524;
        double jupiterMeanAU = 5.203;
        double saturnMeanAU = 9.537;
        double uranusMeanAU = 19.191;
        double neptuneMeanAU = 30.069;

        // Scale factor: adjust this to fit scene
        // Previous orbits: Mercury=40, Earth=100, Neptune=750
        // approximately 100 scene units per AU for inner planets
        double AU_SCALE = 100.0;

        // For outer planets, use logarithmic scaling to keep them visible
        double jupiterScale = 60.0;  // Compress outer planet distances
        double saturnScale = 50.0;
        double uranusScale = 40.0;
        double neptuneScale = 30.0;

        // Create planets with VSOP87-accurate orbits
        planets.add(new Planet("Mercury", Math.max(0.38 * SIZE_SCALE, MIN_PLANET_SIZE), "mercury.png",
                mercuryMeanAU * AU_SCALE, 88, 0.206, 0.03));
        planets.add(new Planet("Venus", 0.95 * SIZE_SCALE, "venus.png",
                venusMeanAU * AU_SCALE, 225, 0.007, 177.4));
        planets.add(new Planet("Earth", SIZE_SCALE, "earth_16k.png",
                earthMeanAU * AU_SCALE, 365, 0.017, 23.5));
        planets.add(new Planet("Mars", Math.max(0.53 * SIZE_SCALE, MIN_PLANET_SIZE), "mars.png",
                marsMeanAU * AU_SCALE, 687, 0.093, 25.2)); // Mars
        planets.add(new Planet("Jupiter", 11.2 * SIZE_SCALE, "jupiter.png",
                jupiterMeanAU * jupiterScale, 4333, 0.048, 3.1)); // Jupiter (compressed)
        planets.add(new Planet("Saturn", 9.45 * SIZE_SCALE, "saturn.png",
                saturnMeanAU * saturnScale, 10759, 0.054, 26.7)); // Saturn (compressed)
        planets.add(new Planet("Uranus", 4.0 * SIZE_SCALE, "uranus.png",
                uranusMeanAU * uranusScale, 30687, 0.047, 97.8)); // Uranus (compressed)
        planets.add(new Planet("Neptune", 3.88 * SIZE_SCALE, "neptune.png",
                neptuneMeanAU * neptuneScale, 60190, 0.009, 28.3)); // Neptune (compressed)

        // Add all planets to celestial bodies list
        celestialBodies.addAll(planets);

        // IMPORTANT: Set initial positions from VSOP87
        double[][] vsopPositions = {
                mercuryPos,
                venusPos,
                earthPos,
                marsPos,
                jupiterPos,
                saturnPos,
                uranusPos,
                neptunePos
        };

        double[] scaleFactors = {
                AU_SCALE,
                AU_SCALE,
                AU_SCALE,
                AU_SCALE,
                jupiterScale,
                saturnScale,
                uranusScale,
                neptuneScale
        };


        // Apply VSOP87 positions to planets
        for (int i = 0; i < planets.size(); i++) {
            Planet planet = planets.get(i);
            double[] pos = vsopPositions[i];
            double scale = scaleFactors[i];

            // X = right/left, Y = up/down, Z = forward/back
            planet.getPlanetGroup().setTranslateX(pos[0] * scale);
            planet.getPlanetGroup().setTranslateY(pos[1] * scale);
            planet.getPlanetGroup().setTranslateZ(pos[2] * scale);

            // Calculate time offset
            double angle = Math.atan2(pos[2], pos[0]);
            if (angle < 0) angle += 2 * Math.PI;
            planetTimeOffsets[i] = (angle / (2 * Math.PI)) * planet.getOrbitPeriod();
        }

        // Create 3D tube orbit paths
        for (Planet planet : planets) {
            Group orbitMesh = create3DOrbitMesh(
                    planet.getOrbitRadius(),
                    planet.getOrbitEccentricity()
            );
            orbitGroup.getChildren().add(orbitMesh);
            planetSystem.getChildren().add(planet.getPlanetGroup());
        }

        // Add Saturn's rings with scaled proportions
        Planet saturn = planets.get(5);
        saturn.addRings("saturn_ring_alpha.png", 9.45 * SIZE_SCALE, 21.7 * SIZE_SCALE);

        orbitGroup.setVisible(orbitsVisible);

        planetSystem.getChildren().add(orbitGroup);

        // Start planet motion
        startOrbitAnimation();
    }

    /**
     * Creates a 3D orbit using a mesh tube
     */
    private Group create3DOrbitMesh(double semiMajorAxis, double eccentricity) {
        // Create orbit path
        EllipticalOrbitPath orbitPath = new EllipticalOrbitPath(semiMajorAxis, eccentricity);

        // Generate mesh (12 radial segments for a smooth tube)
        TubeMeshGenerator generator = new TubeMeshGenerator(12, ORBIT_TUBE_RADIUS);
        Point3D[] points = orbitPath.getOrbitPoints(ORBIT_SEGMENTS);
        TriangleMesh tubeMesh = generator.generateTube(points, orbitPath.isClosed());

        // Create mesh view
        MeshView meshView = new MeshView(tubeMesh);

        // Apply material
        PhongMaterial orbitMaterial = new PhongMaterial();
        orbitMaterial.setDiffuseColor(Color.gray(0.5));
        orbitMaterial.setSpecularColor(Color.gray(0.1));
        orbitMaterial.setSpecularPower(128);
        meshView.setMaterial(orbitMaterial);

        // Enable backface culling for performance
        meshView.setCullFace(CullFace.BACK);

        Group orbitGroup = new Group(meshView);
        orbitGroup.setCache(true);
        orbitGroup.setCacheHint(CacheHint.QUALITY);

        return orbitGroup;
    }

    private void startOrbitAnimation() {
        animationTimer = new AnimationTimer() {
            private long lastUpdate = 0;
            private double accumulator = 0;

            private static final double FIXED_TIMESTEP = 1.0 / 60.0;

            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }

                double deltaTime = (now - lastUpdate) / NANO_TO_SECONDS;
                lastUpdate = now;
                deltaTime = Math.min(deltaTime, 0.1);
                accumulator += deltaTime;

                while (accumulator >= FIXED_TIMESTEP) {
                    time += FIXED_TIMESTEP * TIME_SCALE;

                    // Update each planet with its time offset
                    for (int i = 0; i < planets.size(); i++) {
                        Planet planet = planets.get(i);
                        // Add the planet's specific time offset
                        planet.updatePosition(time + planetTimeOffsets[i]);
                    }

                    accumulator -= FIXED_TIMESTEP;
                }
            }
        };
        animationTimer.start();
    }

    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    public void toggleOrbitVisibility() {
        orbitsVisible = !orbitsVisible;
        orbitGroup.setVisible(orbitsVisible);
    }

    public Group getPlanetSystem() {
        return planetSystem;
    }

    /**
     * Get list of all celestial bodies in order (Sun, Mercury, Venus, etc.)
     */
    public List<CelestialBody> getCelestialBodies() {
        return celestialBodies;
    }

    /**
     * Get a specific celestial body by index
     */
    public CelestialBody getCelestialBody(int index) {
        if (index >= 0 && index < celestialBodies.size()) {
            return celestialBodies.get(index);
        }
        return null;
    }

    /**
     * Get the total number of celestial bodies
     */
    public int getCelestialBodyCount() {
        return celestialBodies.size();
    }
}