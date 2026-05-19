package com.jda.orrery.domain.ephemeris;

import static org.junit.jupiter.api.Assertions.*;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.astronomy.CelestialBody;
import com.jda.orrery.domain.astronomy.NaturalSatellite;
import com.jda.orrery.domain.astronomy.SolarSystem;
import com.jda.orrery.domain.ephemeris.cache.SimpleFrameCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MoonRelativeStateTest {

    private static SolarSystem solarSystem;

    @BeforeAll
    static void setup() {
        solarSystem = new SolarSystem(new AnalyticalEphemerisProvider(), new SimpleFrameCache());
    }

    @Test
    void moonStateIsRelative() {
        NaturalSatellite moon = solarSystem.getSatellites().get(0);
        TimeContext time = new TimeContext(2451545.0, 0.0, 0, 1.0, false);

        FramedState state = moon.getState(time);

        assertTrue(state.isRelative(), "Moon state should be marked as relative to parent");
    }

    @Test
    void moonParentIsEarth() {
        NaturalSatellite moon = solarSystem.getSatellites().get(0);

        CelestialBody parent = moon.getParentOrNull();

        assertNotNull(parent, "Moon should have a parent");
        assertEquals("earth", parent.getId());
    }
}
