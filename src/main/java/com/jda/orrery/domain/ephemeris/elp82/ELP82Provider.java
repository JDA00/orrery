package com.jda.orrery.domain.ephemeris.elp82;

import com.jda.orrery.core.frames.FrameNames;
import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.math.Vec3d;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;

/**
 * Ephemeris provider for the Moon using ELP-2000/82 lunar theory.
 *
 * Returns the Moon's state (position and velocity) relative to Earth's center in J2000 ecliptic
 * coordinates (matching the frame used by planets).
 *
 * Accuracy is approximately 10 arcseconds for the period 1900-2100. Velocity is calculated using
 * numerical differentiation.
 */
public class ELP82Provider implements EphemerisProvider {

    // Convert km to AU (1 AU = 149,597,870.7 km)
    private static final double KM_TO_AU = 1.0 / 149597870.7;

    // Optimal time step for numerical differentiation
    private static final double DT_DAYS = 1.0 / 24.0; // 1 hour in days
    private static final double DT_CENTURIES = DT_DAYS / 36525.0; // Convert to centuries

    @Override
    public FramedState getState(TimeContext time, String bodyId) {
        // Only handles the Moon
        if (!"moon".equalsIgnoreCase(bodyId)) {
            return null;
        }

        // ELP82 uses Julian centuries since J2000.0
        double T = time.getJulianCenturiesSinceJ2000();

        // Get Moon position at time T
        Vec3d position = calculatePositionAtTime(T);
        if (position == null) {
            return null;
        }

        // Calculate velocity using central difference method
        // v = (r(t+dt) - r(t-dt)) / (2*dt)
        Vec3d posMinus = calculatePositionAtTime(T - DT_CENTURIES);
        Vec3d posPlus = calculatePositionAtTime(T + DT_CENTURIES);

        Vec3d velocity;
        if (posMinus == null || posPlus == null) {
            // Fallback to zero velocity if we can't calculate
            velocity = Vec3d.ZERO;
        } else {
            // Calculate velocity in AU/day
            velocity =
                    new Vec3d(
                            (posPlus.x - posMinus.x) / (2.0 * DT_DAYS),
                            (posPlus.y - posMinus.y) / (2.0 * DT_DAYS),
                            (posPlus.z - posMinus.z) / (2.0 * DT_DAYS));
        }

        // Return geocentric position in ecliptic frame (matching Earth's frame)
        return new FramedState(
                position,
                velocity,
                FrameNames.ECLIPJ2000, // Return in ecliptic to match planets
                time.getEphemerisTime(),
                getAccuracy(bodyId),
                true); // Moon position is relative to Earth
    }

    /**
     * Calculate Moon position at a specific time.
     *
     * @param T Julian centuries since J2000.0
     * @return Position in AU in J2000 ecliptic coordinates
     */
    private Vec3d calculatePositionAtTime(double T) {
        // Get Moon position in geocentric ecliptic coordinates
        // Returns [longitude (degrees), latitude (degrees), distance (km)]
        double[] ecliptic = ELP82.getMoonPosition(T);

        double lonRad = Math.toRadians(ecliptic[0]);
        double latRad = Math.toRadians(ecliptic[1]);
        double distanceKm = ecliptic[2];

        // Convert from spherical ecliptic to rectangular ecliptic
        double cosLat = Math.cos(latRad);
        double xEcliptic = distanceKm * cosLat * Math.cos(lonRad);
        double yEcliptic = distanceKm * cosLat * Math.sin(lonRad);
        double zEcliptic = distanceKm * Math.sin(latRad);

        // Return directly in ecliptic coordinates (no conversion to equatorial)
        // This matches the frame of Earth and other planets from VSOP87E
        return new Vec3d(xEcliptic * KM_TO_AU, yEcliptic * KM_TO_AU, zEcliptic * KM_TO_AU);
    }

    @Override
    public boolean supports(String bodyId) {
        return "moon".equalsIgnoreCase(bodyId) || "301".equals(bodyId); // Also support NAIF ID
    }

    @Override
    public double getAccuracy(String bodyId) {
        // ELP82 accuracy is approximately 10 arcseconds
        return supports(bodyId) ? 10.0 : Double.NaN;
    }

    @Override
    public double[] getValidTimeRange() {
        // ELP82 is valid from approximately 1900 to 2100
        // Julian dates for Jan 1, 1900 and Jan 1, 2100
        return new double[] {2415020.5, 2488070.5};
    }

    @Override
    public String getName() {
        return "ELP-2000/82";
    }
}
