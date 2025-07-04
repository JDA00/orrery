package com.jda.orrery.model;

import javafx.animation.AnimationTimer;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.List;

public class SolarSystem {
    // Constants
    private static final double TIME_SCALE = 5.0;
    private static final int ORBIT_SEGMENTS = 180; // For 3D tubes, this is sufficient
    private static final double ORBIT_TUBE_RADIUS = 0.3; // Radius of the 3D tube

    // Performance optimization: Pre-calculate common values
    private static final double TWO_PI = 2 * Math.PI;
    private static final double SEGMENT_ANGLE = TWO_PI / ORBIT_SEGMENTS;
    private static final double NANO_TO_SECONDS = 1_000_000_000.0;

    private final Group planetSystem;
    private final Group orbitGroup;
    private final List<Planet> planets = new ArrayList<>();
    private double time = 0;
    private AnimationTimer animationTimer;
    private boolean orbitsVisible = true;

    public SolarSystem() {
        planetSystem = new Group();
        orbitGroup = new Group();

        // Cache static orbit group
        orbitGroup.setCache(true);
        orbitGroup.setCacheHint(CacheHint.SPEED);

        // Add planets with texture files
        planets.add(new Planet(10, "sun.png", 0, 0, 0, 7.25)); // Sun
        planets.add(new Planet(2, "mercury.png", 30, 88, 0.206, 0.03)); // Mercury
        planets.add(new Planet(4, "venus.png", 50, 225, 0.007, 177.4)); // Venus
        planets.add(new Planet(5, "earth.png", 75, 365, 0.017, 23.5)); // Earth
        planets.add(new Planet(4, "mars.png", 100, 687, 0.093, 25.2)); // Mars
        planets.add(new Planet(10, "jupiter.png", 150, 4333, 0.048, 3.1)); // Jupiter
        planets.add(new Planet(8, "saturn.png", 200, 10759, 0.054, 26.7)); // Saturn
        planets.add(new Planet(7, "uranus.png", 250, 30687, 0.047, 97.8)); // Uranus
        planets.add(new Planet(6, "neptune.png", 300, 60190, 0.009, 28.3)); // Neptune

        // Create 3D tube orbit paths
        for (Planet planet : planets) {
            if (planet.getOrbitRadius() > 0) {
                Group orbitRing = create3DOrbitRing(
                        planet.getOrbitRadius(),
                        planet.getOrbitEccentricity()
                );
                orbitGroup.getChildren().add(orbitRing);
            }
            planetSystem.getChildren().add(planet.getPlanetGroup());
        }

        planetSystem.getChildren().add(orbitGroup);

        // Start planet motion
        startOrbitAnimation();
    }

    /**
     * Creates a high-quality 3D orbit using overlapping boxes to eliminate seams
     */
    private Group create3DOrbitRing(double semiMajorAxis, double eccentricity) {
        Group orbitRing = new Group();

        // Calculate ellipse parameters
        double semiMinorAxis = semiMajorAxis * Math.sqrt(1 - eccentricity * eccentricity);
        double centerOffset = semiMajorAxis * eccentricity;

        // Material for orbit tubes with less specular to reduce edge highlights
        PhongMaterial orbitMaterial = new PhongMaterial();
        orbitMaterial.setDiffuseColor(Color.gray(0.65));
        orbitMaterial.setSpecularColor(Color.gray(0.3)); // Less specular
        orbitMaterial.setSpecularPower(32); // Lower power for softer highlights

        // Create tube segments using boxes with overlap
        for (int i = 0; i < ORBIT_SEGMENTS; i++) {
            double angle1 = i * SEGMENT_ANGLE;
            double angle2 = (i + 1) * SEGMENT_ANGLE;

            // Calculate positions
            double x1 = semiMajorAxis * Math.cos(angle1) - centerOffset;
            double y1 = semiMinorAxis * Math.sin(angle1);
            double x2 = semiMajorAxis * Math.cos(angle2) - centerOffset;
            double y2 = semiMinorAxis * Math.sin(angle2);

            // Create box segments to overlap
            double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            double overlapFactor = 1.1; // 10% overlap
            Box segment = new Box(length * overlapFactor, ORBIT_TUBE_RADIUS * 2, ORBIT_TUBE_RADIUS * 2);
            segment.setMaterial(orbitMaterial);

            // Position the segment at midpoint
            double midX = (x1 + x2) / 2;
            double midY = (y1 + y2) / 2;
            segment.setTranslateX(midX);
            segment.setTranslateY(midY);
            segment.setTranslateZ(0);

            // Rotate to align with orbit direction
            double angle = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
            segment.setRotate(angle);

            orbitRing.getChildren().add(segment);
        }

        // Apply a subtle blur effect to smooth out any remaining seams
        javafx.scene.effect.GaussianBlur blur = new javafx.scene.effect.GaussianBlur(0.5);
        orbitRing.setEffect(blur);

        // Cache the entire orbit for performance
        orbitRing.setCache(true);
        orbitRing.setCacheHint(CacheHint.QUALITY);

        return orbitRing;
    }

    private void startOrbitAnimation() {
        animationTimer = new AnimationTimer() {
            private long lastUpdate = 0;
            private double accumulator = 0;

            // Timestep for smooth animation
            private static final double FIXED_TIMESTEP = 1.0 / 60.0; // 60 FPS

            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }

                // Calculate delta time in seconds
                double deltaTime = (now - lastUpdate) / NANO_TO_SECONDS;
                lastUpdate = now;

                // Clamp large delta times
                deltaTime = Math.min(deltaTime, 0.1);

                // Accumulate time for fixed timestep
                accumulator += deltaTime;

                // Update with fixed timestep for smooth motion
                while (accumulator >= FIXED_TIMESTEP) {
                    time += FIXED_TIMESTEP * TIME_SCALE;

                    // Update all planets with fixed timestep
                    for (int i = 0; i < planets.size(); i++) {
                        planets.get(i).updatePosition(time);
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

}