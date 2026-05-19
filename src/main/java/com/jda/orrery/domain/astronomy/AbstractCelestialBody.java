package com.jda.orrery.domain.astronomy;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.core.math.Vec3d;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import com.jda.orrery.domain.ephemeris.cache.EphemerisCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Abstract base implementation of CelestialBody.
 *
 * Provides common functionality for all celestial bodies including parent-child relationship
 * management and state caching.
 */
public abstract class AbstractCelestialBody implements CelestialBody {
    private static final Logger LOGGER = Logging.logger(AbstractCelestialBody.class);

    protected final String id;
    protected final String name;
    protected final BodyType bodyType;
    protected final double mass;
    protected final double radius;
    protected final EphemerisProvider ephemerisProvider;
    protected final EphemerisCache cache;
    protected final String referenceFrame;

    protected CelestialBody parent;
    protected final List<CelestialBody> children;

    /**
     * Create a new celestial body.
     *
     * @param id Unique identifier (e.g., NAIF ID)
     * @param name Display name
     * @param bodyType Classification
     * @param mass Mass in kg
     * @param radius Mean radius in km
     * @param ephemerisProvider Provider for position calculations
     * @param cache Ephemeris cache used by getState()
     * @param referenceFrame Coordinate reference frame
     */
    protected AbstractCelestialBody(
            String id,
            String name,
            BodyType bodyType,
            double mass,
            double radius,
            EphemerisProvider ephemerisProvider,
            EphemerisCache cache,
            String referenceFrame) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.bodyType = Objects.requireNonNull(bodyType, "Body type cannot be null");
        this.mass = mass;
        this.radius = radius;
        this.ephemerisProvider =
                Objects.requireNonNull(ephemerisProvider, "Ephemeris provider cannot be null");
        this.cache = Objects.requireNonNull(cache, "Cache cannot be null");
        this.referenceFrame =
                Objects.requireNonNull(referenceFrame, "Reference frame cannot be null");
        this.children = new ArrayList<>();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public BodyType getBodyType() {
        return bodyType;
    }

    @Override
    public Optional<CelestialBody> getParent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public CelestialBody getParentOrNull() {
        return parent;
    }

    /** Set the parent body (package-private for controlled access). */
    void setParent(CelestialBody parent) {
        this.parent = parent;
    }

    @Override
    public List<CelestialBody> getChildren() {
        return new ArrayList<>(children); // Return defensive copy
    }

    @Override
    public void addChild(CelestialBody child) {
        if (child == null) {
            throw new IllegalArgumentException("Child cannot be null");
        }
        if (child == this) {
            throw new IllegalArgumentException("Body cannot be its own child");
        }

        children.add(child);

        // Set parent relationship if it's an AbstractCelestialBody
        if (child instanceof AbstractCelestialBody) {
            ((AbstractCelestialBody) child).setParent(this);
        }
    }

    @Override
    public double getMass() {
        return mass;
    }

    @Override
    public double getRadius() {
        return radius;
    }

    @Override
    public FramedState getState(TimeContext time) {
        return cache.getState(id, time, this::calculateState);
    }

    /**
     * Calculate the state for this body at the given time.
     *
     * This method contains the pure calculation logic, separated from caching. For satellites,
     * it preserves the relative coordinates from ephemeris. For planets/sun, it returns absolute
     * coordinates.
     *
     * @param time The time context
     * @return The calculated body state
     */
    private FramedState calculateState(TimeContext time) {
        // Get state from ephemeris provider
        FramedState state = ephemerisProvider.getState(time, id);

        if (state == null) {
            // Fallback to origin if no ephemeris data
            state =
                    new FramedState(
                            Vec3d.ZERO,
                            Vec3d.ZERO,
                            referenceFrame,
                            time.getEphemerisTime(),
                            Double.NaN,
                            false);
        }

        return state;
    }

    @Override
    public EphemerisProvider getEphemerisProvider() {
        return ephemerisProvider;
    }

    @Override
    public String getReferenceFrame() {
        return referenceFrame;
    }

    @Override
    public double getAccuracy(TimeContext time) {
        return ephemerisProvider.getAccuracy(id);
    }

    @Override
    public double[] getValidTimeRange() {
        return ephemerisProvider.getValidTimeRange();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AbstractCelestialBody)) return false;

        AbstractCelestialBody other = (AbstractCelestialBody) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format(
                "%s[id=%s, name=%s, type=%s]", getClass().getSimpleName(), id, name, bodyType);
    }
}
