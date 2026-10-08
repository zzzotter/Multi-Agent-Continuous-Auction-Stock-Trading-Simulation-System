package com.trading.model;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class MarketDataTest {

    @Test
    void spreadAndMidPrice() {
        MarketData data = new MarketData("AAPL",
                149.0, 100, 151.0, 100, 150.0,
                Collections.emptyList(), Collections.emptyList());

        assertEquals(2.0, data.getSpread(), 0.001);
        assertEquals(150.0, data.getMidPrice(), 0.001);
    }

    @Test
    void nullBidGivesNullSpread() {
        MarketData data = new MarketData("AAPL",
                null, null, 151.0, 100, null,
                Collections.emptyList(), Collections.emptyList());

        assertNull(data.getSpread());
        assertNull(data.getMidPrice());
    }
}