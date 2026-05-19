package com.jda.orrery.core.time;

import com.jda.orrery.core.logging.Logging;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Immutable snapshot of time for a single frame.
 *
 * This class represents a specific moment in time and provides conversions between different
 * astronomical time scales. All celestial calculations within a frame should use the same
 * TimeContext to ensure consistency.
 *
 * The internal representation is in Barycentric Dynamical Time (TDB) as this is the time scale
 * used by most ephemeris systems (VSOP87, JPL DE series).
 */
public final class TimeContext {

    private static final Logger LOGGER = Logging.logger(TimeContext.class);
    private static final AtomicBoolean UT1_WARNING_LOGGED = new AtomicBoolean(false);

    // Core time value in TDB (Barycentric Dynamical Time)
    private final double julianDateTDB;

    // Frame timing information
    private final double deltaSeconds; // Real-world time since last frame
    private final int frameNumber; // Sequential frame counter
    private final double simulationSpeed; // Time acceleration factor
    private final boolean paused; // Whether simulation is paused

    // Cached conversions (lazy initialization)
    private volatile Double julianDateUTC;
    private volatile Double julianDateTAI;
    private volatile Double julianDateTT;
    private volatile Double julianDateTCB;
    private volatile Double julianDateUT1;
    private volatile Double julianDateGPS;

    // Constants
    private static final double J2000_EPOCH = 2451545.0; // J2000.0 = 2000-01-01 12:00:00 TT
    private static final double MJD_EPOCH = 2400000.5; // Modified Julian Date offset
    private static final double TT_TAI_OFFSET = 32.184; // TT = TAI + 32.184 seconds
    private static final double GPS_TAI_OFFSET = -19.0; // GPS = TAI - 19 seconds
    private static final double TCB_LB = 1.550519768e-8; // TCB-TDB linear coefficient

    /**
     * Create a TimeContext from a Julian Date in TDB.
     *
     * @param julianDateTDB Julian Date in Barycentric Dynamical Time
     * @param deltaSeconds Real-world seconds since last frame
     * @param frameNumber Sequential frame number
     * @param simulationSpeed Time acceleration factor (1.0 = real time)
     * @param paused Whether the simulation is paused
     */
    public TimeContext(
            double julianDateTDB,
            double deltaSeconds,
            int frameNumber,
            double simulationSpeed,
            boolean paused) {
        this.julianDateTDB = julianDateTDB;
        this.deltaSeconds = deltaSeconds;
        this.frameNumber = frameNumber;
        this.simulationSpeed = simulationSpeed;
        this.paused = paused;
    }

    /**
     * Create a TimeContext from a Julian Date in TDB (legacy constructor). Defaults to not paused
     * for backward compatibility.
     *
     * @param julianDateTDB Julian Date in Barycentric Dynamical Time
     * @param deltaSeconds Real-world seconds since last frame
     * @param frameNumber Sequential frame number
     * @param simulationSpeed Time acceleration factor (1.0 = real time)
     */
    public TimeContext(
            double julianDateTDB, double deltaSeconds, int frameNumber, double simulationSpeed) {
        this(julianDateTDB, deltaSeconds, frameNumber, simulationSpeed, false);
    }

    /**
     * Create a TimeContext from a Julian Date in UTC. Useful for initialization from civil time.
     *
     * @param julianDateUTC Julian Date in UTC
     * @param deltaSeconds Real-world seconds since last frame
     * @param frameNumber Sequential frame number
     * @param simulationSpeed Time acceleration factor
     * @return New TimeContext instance
     */
    public static TimeContext fromUTC(
            double julianDateUTC, double deltaSeconds, int frameNumber, double simulationSpeed) {
        // Convert UTC to TDB via TAI and TT
        double jdTAI = LeapSecondTable.utcToTAI(julianDateUTC);
        double jdTT = jdTAI + (TT_TAI_OFFSET / 86400.0);
        double jdTDB = ttToTDB(jdTT);

        TimeContext context =
                new TimeContext(jdTDB, deltaSeconds, frameNumber, simulationSpeed, false);
        // Cache the UTC value we started with
        context.julianDateUTC = julianDateUTC;
        context.julianDateTAI = jdTAI;
        context.julianDateTT = jdTT;

        return context;
    }

    /**
     * Get time in the specified time scale.
     *
     * @param scale The desired time scale
     * @return Julian Date in the requested scale
     */
    public double getTime(TimeScale scale) {
        Objects.requireNonNull(scale, "Time scale cannot be null");

        return switch (scale) {
            case TDB -> getJulianDateTDB();
            case TT -> getJulianDateTT();
            case TAI -> getJulianDateTAI();
            case UTC -> getJulianDateUTC();
            case TCB -> getJulianDateTCB();
            case UT1 -> getJulianDateUT1();
            case GPS -> getJulianDateGPS();
        };
    }

    /** Get Julian Date in TDB (native storage). */
    public double getJulianDateTDB() {
        return julianDateTDB;
    }

    /** Get Julian Date in Terrestrial Time. TT is used for geocentric ephemerides. */
    public double getJulianDateTT() {
        if (julianDateTT == null) {
            synchronized (this) {
                if (julianDateTT == null) {
                    julianDateTT = tdbToTT(julianDateTDB);
                }
            }
        }
        return julianDateTT;
    }

    /** Get Julian Date in International Atomic Time. */
    public double getJulianDateTAI() {
        if (julianDateTAI == null) {
            synchronized (this) {
                if (julianDateTAI == null) {
                    double jdTT = getJulianDateTT();
                    julianDateTAI = jdTT - (TT_TAI_OFFSET / 86400.0);
                }
            }
        }
        return julianDateTAI;
    }

    /**
     * Get Julian Date in Coordinated Universal Time. Note: UTC has discontinuities at leap seconds.
     */
    public double getJulianDateUTC() {
        if (julianDateUTC == null) {
            synchronized (this) {
                if (julianDateUTC == null) {
                    double jdTAI = getJulianDateTAI();
                    julianDateUTC = LeapSecondTable.taiToUTC(jdTAI);
                }
            }
        }
        return julianDateUTC;
    }

    /** Get Julian Date in Barycentric Coordinate Time. Used by IAU standards and Gaia catalog. */
    public double getJulianDateTCB() {
        if (julianDateTCB == null) {
            synchronized (this) {
                if (julianDateTCB == null) {
                    // TCB - TDB = L_B * (JD - T0) * 86400 seconds
                    // where T0 = J2000.0 and L_B = 1.550519768e-8
                    double daysSinceJ2000 = julianDateTDB - J2000_EPOCH;
                    double tcbOffset = TCB_LB * daysSinceJ2000 * 86400.0;
                    julianDateTCB = julianDateTDB + (tcbOffset / 86400.0);
                }
            }
        }
        return julianDateTCB;
    }

    /**
     * Get Julian Date in Universal Time (UT1). Required for Earth rotation and sidereal time
     * calculations.
     *
     * <b>Approximation:</b> this implementation assumes DUT1 = 0, giving an error of up to ~0.9s
     * relative to true UT1. For accurate sidereal calculations, an IERS bulletin reader should
     * populate DUT1. A one-shot warning is logged the first time this method is called so the
     * assumption is never silent.
     */
    public double getJulianDateUT1() {
        if (julianDateUT1 == null) {
            synchronized (this) {
                if (julianDateUT1 == null) {
                    if (UT1_WARNING_LOGGED.compareAndSet(false, true)) {
                        LOGGER.warning(
                                "getJulianDateUT1() is using DUT1=0 (error up to ~0.9s). "
                                        + "Implement IERS bulletin reader for accurate UT1.");
                    }
                    // UT1 ≈ UTC + DUT1; with DUT1 assumed zero.
                    julianDateUT1 = getJulianDateUTC();
                }
            }
        }
        return julianDateUT1;
    }

    /** Get Julian Date in GPS Time. GPS time is continuous (no leap seconds) since Jan 6, 1980. */
    public double getJulianDateGPS() {
        if (julianDateGPS == null) {
            synchronized (this) {
                if (julianDateGPS == null) {
                    double jdTAI = getJulianDateTAI();
                    julianDateGPS = jdTAI + (GPS_TAI_OFFSET / 86400.0);
                }
            }
        }
        return julianDateGPS;
    }

    /**
     * Get Julian centuries since J2000.0 epoch. This is the T parameter used in many astronomical
     * calculations.
     */
    public double getJulianCenturiesSinceJ2000() {
        return (julianDateTDB - J2000_EPOCH) / 36525.0;
    }

    /** Get Julian millennia since J2000.0 epoch. Used in some long-period calculations. */
    public double getJulianMillenniaSinceJ2000() {
        return (julianDateTDB - J2000_EPOCH) / 365250.0;
    }

    /** Get Modified Julian Date (MJD = JD - 2400000.5). MJD starts at midnight rather than noon. */
    public double getModifiedJulianDate() {
        return julianDateTDB - MJD_EPOCH;
    }

    /** Get ephemeris time in seconds since J2000.0. This format is used by SPICE kernels. */
    public double getEphemerisTime() {
        return (julianDateTDB - J2000_EPOCH) * 86400.0;
    }

    /** Convert TDB to TT. The difference is periodic with amplitude ~1.7ms. */
    private static double tdbToTT(double jdTDB) {
        // Simplified formula (accurate to ~2ms)
        // Full formula requires Earth's orbital elements
        double T = (jdTDB - J2000_EPOCH) / 36525.0;
        double M = Math.toRadians(357.5277233 + 35999.05034 * T);
        double deltaT = -0.001657 * Math.sin(M) - 0.000022 * Math.sin(2 * M);
        return jdTDB + (deltaT / 86400.0);
    }

    /** Convert TT to TDB. */
    private static double ttToTDB(double jdTT) {
        // Inverse of tdbToTT
        double T = (jdTT - J2000_EPOCH) / 36525.0;
        double M = Math.toRadians(357.5277233 + 35999.05034 * T);
        double deltaT = 0.001657 * Math.sin(M) + 0.000022 * Math.sin(2 * M);
        return jdTT + (deltaT / 86400.0);
    }

    // Frame timing getters

    public double getDeltaSeconds() {
        return deltaSeconds;
    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public double getSimulationSpeed() {
        return simulationSpeed;
    }

    /**
     * Check if the simulation is paused.
     *
     * Pause state is a first-class concept, distinct from simulationSpeed == 0. This provides
     * semantic clarity and future-proofs the API for features like step mode.
     *
     * @return true if the simulation is paused, false otherwise
     */
    public boolean isPaused() {
        return paused;
    }

    /** Check if this time context is valid. */
    public boolean isValid() {
        return julianDateTDB > 0
                && frameNumber >= 0
                && !Double.isNaN(julianDateTDB)
                && !Double.isNaN(deltaSeconds)
                && !Double.isNaN(simulationSpeed);
    }

    @Override
    public String toString() {
        return String.format(
                "TimeContext[JD_TDB=%.6f, frame=%d, speed=%.1fx, paused=%s]",
                julianDateTDB, frameNumber, simulationSpeed, paused);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TimeContext)) return false;
        TimeContext other = (TimeContext) obj;
        return Double.compare(julianDateTDB, other.julianDateTDB) == 0
                && frameNumber == other.frameNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(julianDateTDB, frameNumber);
    }
}
