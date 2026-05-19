package com.jda.orrery.domain.ephemeris.vsop87.data;

// VSOP87-Multilang http://www.celestialprogramming.com/vsop87-multilang/index.html
// Greg Miller (gmiller@gregmiller.net) 2019.  Released as Public Domain

public class VSOP87ELarge {
    public static double[] getEarth(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeEarth.earthX(t);
        temp[1] = VSOP87ELargeEarth.earthY(t);
        temp[2] = VSOP87ELargeEarth.earthZ(t);
        return temp;
    }

    public static double[] getJupiter(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeJupiter.jupiterX(t);
        temp[1] = VSOP87ELargeJupiter.jupiterY(t);
        temp[2] = VSOP87ELargeJupiter.jupiterZ(t);
        return temp;
    }

    public static double[] getMars(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeMars.marsX(t);
        temp[1] = VSOP87ELargeMars.marsY(t);
        temp[2] = VSOP87ELargeMars.marsZ(t);
        return temp;
    }

    public static double[] getMercury(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeMercury.mercuryX(t);
        temp[1] = VSOP87ELargeMercury.mercuryY(t);
        temp[2] = VSOP87ELargeMercury.mercuryZ(t);
        return temp;
    }

    public static double[] getNeptune(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeNeptune.neptuneX(t);
        temp[1] = VSOP87ELargeNeptune.neptuneY(t);
        temp[2] = VSOP87ELargeNeptune.neptuneZ(t);
        return temp;
    }

    public static double[] getSaturn(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeSaturn.saturnX(t);
        temp[1] = VSOP87ELargeSaturn.saturnY(t);
        temp[2] = VSOP87ELargeSaturn.saturnZ(t);
        return temp;
    }

    public static double[] getUranus(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeUranus.uranusX(t);
        temp[1] = VSOP87ELargeUranus.uranusY(t);
        temp[2] = VSOP87ELargeUranus.uranusZ(t);
        return temp;
    }

    public static double[] getVenus(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeVenus.venusX(t);
        temp[1] = VSOP87ELargeVenus.venusY(t);
        temp[2] = VSOP87ELargeVenus.venusZ(t);
        return temp;
    }

    public static double[] getSun(double t) {
        double[] temp = new double[3];
        temp[0] = VSOP87ELargeSun.sunX(t);
        temp[1] = VSOP87ELargeSun.sunY(t);
        temp[2] = VSOP87ELargeSun.sunZ(t);
        return temp;
    }
}
