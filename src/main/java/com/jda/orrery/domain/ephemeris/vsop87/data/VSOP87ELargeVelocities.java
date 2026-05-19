package com.jda.orrery.domain.ephemeris.vsop87.data;

// VSOP87-Multilang http://www.celestialprogramming.com/vsop87-multilang/index.html
// Greg Miller (gmiller@gregmiller.net) 2019.  Released as Public Domain

public class VSOP87ELargeVelocities {
    public static double[] getEarth(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesEarth.earthX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesEarth.earthY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesEarth.earthZ(t) / 365250.0;
        return temp;
    }

    public static double[] getJupiter(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesJupiter.jupiterX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesJupiter.jupiterY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesJupiter.jupiterZ(t) / 365250.0;
        return temp;
    }

    public static double[] getMars(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesMars.marsX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesMars.marsY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesMars.marsZ(t) / 365250.0;
        return temp;
    }

    public static double[] getMercury(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesMercury.mercuryX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesMercury.mercuryY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesMercury.mercuryZ(t) / 365250.0;
        return temp;
    }

    public static double[] getNeptune(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesNeptune.neptuneX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesNeptune.neptuneY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesNeptune.neptuneZ(t) / 365250.0;
        return temp;
    }

    public static double[] getSaturn(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesSaturn.saturnX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesSaturn.saturnY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesSaturn.saturnZ(t) / 365250.0;
        return temp;
    }

    public static double[] getUranus(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesUranus.uranusX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesUranus.uranusY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesUranus.uranusZ(t) / 365250.0;
        return temp;
    }

    public static double[] getVenus(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesVenus.venusX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesVenus.venusY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesVenus.venusZ(t) / 365250.0;
        return temp;
    }

    public static double[] getSun(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVelocitiesSun.sunX(t) / 365250.0;
        temp[1] = VSOP87ELargeVelocitiesSun.sunY(t) / 365250.0;
        temp[2] = VSOP87ELargeVelocitiesSun.sunZ(t) / 365250.0;
        return temp;
    }
}
