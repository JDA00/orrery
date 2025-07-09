package com.jda.orrery.model;

import javafx.geometry.Point3D;

/**
 * Implementation of OrbitPath for simple elliptical orbits.
 * Uses standard orbital mechanics with semi-major axis and eccentricity.
 */
public class EllipticalOrbitPath implements OrbitPath {
    private final double semiMajorAxis;
    private final double eccentricity;
    private final double centerOffset;
    private final double semiMinorAxis;

    public EllipticalOrbitPath(double semiMajorAxis, double eccentricity) {
        this.semiMajorAxis = semiMajorAxis;
        this.eccentricity = eccentricity;
        this.semiMinorAxis = semiMajorAxis * Math.sqrt(1 - eccentricity * eccentricity);
        this.centerOffset = semiMajorAxis * eccentricity;
    }

    @Override
    public Point3D[] getOrbitPoints(int numSegments) {
        // Create numSegments + 1 points to close the loop
        Point3D[] points = new Point3D[numSegments + 1];

        for (int i = 0; i <= numSegments; i++) {
            double angle = (2.0 * Math.PI * i) / numSegments;

            // Calculate position on ellipse
            double x = semiMajorAxis * Math.cos(angle) - centerOffset;
            double z = semiMinorAxis * Math.sin(angle);

            // Y = 0 for horizontal orbit plane
            points[i] = new Point3D(x, 0, z);
        }

        return points;
    }

    @Override
    public boolean isClosed() {
        return true;
    }

    @Override
    public double suggestedTubeRadius() {
        // Scale tube radius based on orbit size
        return 0.1 + (semiMajorAxis / 1000.0) * 0.05;
    }
}