package com.trading.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void createValidOrder() {
        Order order = new Order("AAPL", OrderSide.BUY, 150.0, 100, "agent1");

        assertEquals("AAPL", order.getSymbol());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(150.0, order.getPrice());
        assertEquals(100, order.getQuantity());
        assertEquals(100, order.getRemainingQuantity());
        assertFalse(order.isFilled());
        assertEquals("agent1", order.getAgentId());
    }

    @Test
    void invalidPriceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order("AAPL", OrderSide.BUY, -1, 100, "agent1"));
    }

    @Test
    void invalidQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order("AAPL", OrderSide.BUY, 150.0, 0, "agent1"));
    }

    @Test
    void invalidSymbolThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order("", OrderSide.BUY, 150.0, 100, "agent1"));
    }

    @Test
    void reduceRemainingWorks() {
        Order order = new Order("AAPL", OrderSide.BUY, 150.0, 100, "agent1");
        order.reduceRemaining(30);
        assertEquals(70, order.getRemainingQuantity());
        assertFalse(order.isFilled());

        order.reduceRemaining(70);
        assertEquals(0, order.getRemainingQuantity());
        assertTrue(order.isFilled());
    }

    @Test
    void reduceTooMuchThrows() {
        Order order = new Order("AAPL", OrderSide.BUY, 150.0, 100, "agent1");
        assertThrows(IllegalArgumentException.class, () -> order.reduceRemaining(101));
    }

    @Test
    void orderIdsAreUnique() {
        Order o1 = new Order("AAPL", OrderSide.BUY, 150.0, 100, "agent1");
        Order o2 = new Order("AAPL", OrderSide.BUY, 150.0, 100, "agent1");
        assertNotEquals(o1.getOrderId(), o2.getOrderId());
    }
}