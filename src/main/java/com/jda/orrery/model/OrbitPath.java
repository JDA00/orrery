package com.jda.orrery.model;

import javafx.geometry.Point3D;

/**
 * Interface for providing 3D points along an orbital path.
 * This abstraction allows for different orbit calculation methods
 * (simple elliptical, VSOP87, etc.) to be used interchangeably.
 */
public interface OrbitPath {
    /**
     * Get an array of 3D points representing the orbit path.
     */
    Point3D[] getOrbitPoints(int numSegments);

    /**
     * Whether this orbit forms a closed loop.
     */
    boolean isClosed();

    /**
     * Suggested tube radius for this orbit.
     * Can be used to vary tube thickness based on orbit size.
     */
    default double suggestedTubeRadius() {
        return 0.1;
    }
}