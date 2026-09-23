package com.trading.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradeTest {

    @Test
    void createValidTrade() {
        Order buyOrder = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer1");
        Order sellOrder = new Order("AAPL", OrderSide.SELL, 149.0, 50, "seller1");

        Trade trade = new Trade("AAPL", 149.0, 50, buyOrder, sellOrder);

        assertEquals("AAPL", trade.getSymbol());
        assertEquals(149.0, trade.getPrice());
        assertEquals(50, trade.getQuantity());
        assertEquals(buyOrder.getOrderId(), trade.getBuyOrderId());
        assertEquals(sellOrder.getOrderId(), trade.getSellOrderId());
        assertEquals("buyer1", trade.getBuyerAgentId());
        assertEquals("seller1", trade.getSellerAgentId());
    }

    @Test
    void getAmountCalculatesCorrectly() {
        Order buyOrder = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer1");
        Order sellOrder = new Order("AAPL", OrderSide.SELL, 149.0, 50, "seller1");

        Trade trade = new Trade("AAPL", 149.0, 50, buyOrder, sellOrder);

        // 149.0 * 50 = 7450.0
        assertEquals(7450.0, trade.getAmount(), 0.001);
    }

    @Test
    void involvesReturnsTrueForParticipant() {
        Order buyOrder = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer1");
        Order sellOrder = new Order("AAPL", OrderSide.SELL, 149.0, 50, "seller1");

        Trade trade = new Trade("AAPL", 149.0, 50, buyOrder, sellOrder);

        assertTrue(trade.involves("buyer1"));
        assertTrue(trade.involves("seller1"));
        assertFalse(trade.involves("other"));
    }

    @Test
    void invalidQuantityThrows() {
        Order buyOrder = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer1");
        Order sellOrder = new Order("AAPL", OrderSide.SELL, 149.0, 50, "seller1");

        assertThrows(IllegalArgumentException.class,
                () -> new Trade("AAPL", 149.0, 0, buyOrder, sellOrder));
    }

    @Test
    void buyOrderMustBeBuySide() {
        // 故意构造一个 "BUY 参数传 SELL 订单" 的情况
        Order wrongOrder = new Order("AAPL", OrderSide.SELL, 150.0, 100, "buyer1");
        Order sellOrder = new Order("AAPL", OrderSide.SELL, 149.0, 50, "seller1");

        assertThrows(IllegalArgumentException.class,
                () -> new Trade("AAPL", 149.0, 50, wrongOrder, sellOrder));
    }

    @Test
    void tradeIdsAreUnique() {
        Order buy1 = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer1");
        Order sell1 = new Order("AAPL", OrderSide.SELL, 149.0, 50, "seller1");
        Order buy2 = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer2");
        Order sell2 = new Order("AAPL", OrderSide.SELL, 149.0, 50, "seller2");

        Trade t1 = new Trade("AAPL", 149.0, 50, buy1, sell1);
        Trade t2 = new Trade("AAPL", 149.0, 50, buy2, sell2);

        assertNotEquals(t1.getTradeId(), t2.getTradeId());
    }
}
