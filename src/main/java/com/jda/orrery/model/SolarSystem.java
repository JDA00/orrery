package com.jda.orrery.model;

import javafx.animation.AnimationTimer;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.shape.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class SolarSystem {
    private final Group planetSystem;
    private final Group orbitLines;
    private boolean orbitsVisible = true;
    private final List<Planet> planets = new ArrayList<>();
    private double time = 0;

    public SolarSystem() {
        planetSystem = new Group();
        orbitLines = new Group();

        // Add planets with texture files
        planets.add(new Planet(10, "sun.png", 0, 0, 0, 7.25)); // Sun
        planets.add(new Planet(2, "mercury.png", 30, 88, 0.206, 0.03)); // Mercury (Almost no tilt)
        planets.add(new Planet(4, "venus.png", 50, 225, 0.007, 177.4)); // Venus (Tilted upside-down)
        planets.add(new Planet(5, "earth.png", 75, 365, 0.017, 23.5)); // Earth (Seasons)
        planets.add(new Planet(4, "mars.png", 100, 687, 0.093, 25.2)); // Mars
        planets.add(new Planet(10, "jupiter.png", 150, 4333, 0.048, 3.1)); // Jupiter (Minimal tilt)
        planets.add(new Planet(8, "saturn.png", 200, 10759, 0.054, 26.7)); // Saturn
        planets.add(new Planet(7, "uranus.png", 250, 30687, 0.047, 97.8)); // Uranus (Rolls on its side)
        planets.add(new Planet(6, "neptune.png", 300, 60190, 0.009, 28.3)); // Neptune


        // Create orbit paths
        for (Planet planet : planets) {
            if (planet.getOrbitRadius() > 0) {
                Path orbit = drawOrbitPath(0, 0, planet.getOrbitRadius());
                orbit.setTranslateX(0);
                orbit.setTranslateY(0);
                orbit.setTranslateZ(0);
                orbit.setDepthTest(DepthTest.DISABLE);
                orbit.setViewOrder(1000);

                orbitLines.getChildren().add(orbit);
            }
            planetSystem.getChildren().add(planet.getPlanetGroup());
        }



        planetSystem.getChildren().addAll(orbitLines);

        // Start planet motion
        startOrbitAnimation();
    }






    private void startOrbitAnimation() {
        new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }

                double deltaTime = (now - lastUpdate) / 1_000_000_000.0;
                lastUpdate = now;

                time += deltaTime * 5;

                for (Planet planet : planets) {
                    planet.updatePosition(time);
                }
            }
        }.start();
    }



    public void toggleOrbitVisibility() {
        orbitsVisible = !orbitsVisible;
        orbitLines.setVisible(orbitsVisible);
    }

    public Group getPlanetSystem() {
        return planetSystem;
    }

    public Group getOrbitLines(){return orbitLines;}

    public List<Planet> getPlanets() {
        return planets;
    }






}


