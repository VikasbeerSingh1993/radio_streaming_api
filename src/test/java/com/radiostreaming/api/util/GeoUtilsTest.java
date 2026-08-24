package com.radiostreaming.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    @Test
    void samePointIsZero() {
        assertEquals(0.0, GeoUtils.haversineKm(31.62, 74.87, 31.62, 74.87), 0.0001);
    }

    @Test
    void amritsarToDelhiIsHundredsOfKilometers() {
        double km = GeoUtils.haversineKm(31.634, 74.872, 28.6139, 77.209);
        assertTrue(km > 350 && km < 500, "expected ~400km, got " + km);
    }
}
