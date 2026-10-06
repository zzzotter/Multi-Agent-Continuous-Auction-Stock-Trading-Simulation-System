package com.trading.engine;

import com.trading.model.Order;
import com.trading.model.OrderSide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook book;

    @BeforeEach
    void setUp() {
        book = new OrderBook("AAPL");
    }

    @Test
    void addAndGetBestBid() {
        Order buy1 = new Order("AAPL", OrderSide.BUY, 150.0, 100, "a1");
        Order buy2 = new Order("AAPL", OrderSide.BUY, 151.0, 100, "a2");

        book.addOrder(buy1);
        book.addOrder(buy2);

        // 价格高的优先
        assertEquals(151.0, book.getBestBid().getPrice());
        assertEquals(2, book.getBidCount());
    }

    @Test
    void addAndGetBestAsk() {
        Order sell1 = new Order("AAPL", OrderSide.SELL, 152.0, 100, "a1");
        Order sell2 = new Order("AAPL", OrderSide.SELL, 151.0, 100, "a2");

        book.addOrder(sell1);
        book.addOrder(sell2);

        // 价格低的优先
        assertEquals(151.0, book.getBestAsk().getPrice());
    }

    @Test
    void samePriceEarlierFirst() throws InterruptedException {
        Order buy1 = new Order("AAPL", OrderSide.BUY, 150.0, 100, "a1");
        Thread.sleep(5);  // 让时间戳不同
        Order buy2 = new Order("AAPL", OrderSide.BUY, 150.0, 100, "a2");

        book.addOrder(buy2);
        book.addOrder(buy1);

        // 同价格，时间早的优先
        assertEquals(buy1.getOrderId(), book.getBestBid().getOrderId());
    }

    @Test
    void removeOrderWorks() {
        Order buy = new Order("AAPL", OrderSide.BUY, 150.0, 100, "a1");
        book.addOrder(buy);

        assertTrue(book.removeOrder(buy));
        assertNull(book.getBestBid());
        assertEquals(0, book.getBidCount());
    }

    @Test
    void topBidsReturnsCorrectOrder() {
        book.addOrder(new Order("AAPL", OrderSide.BUY, 148.0, 100, "a1"));
        book.addOrder(new Order("AAPL", OrderSide.BUY, 150.0, 100, "a2"));
        book.addOrder(new Order("AAPL", OrderSide.BUY, 149.0, 100, "a3"));

        List<Order> top2 = book.getTopBids(2);

        assertEquals(2, top2.size());
        assertEquals(150.0, top2.get(0).getPrice());
        assertEquals(149.0, top2.get(1).getPrice());
        // 原队列没被破坏
        assertEquals(3, book.getBidCount());
    }

    @Test
    void wrongSymbolThrows() {
        Order wrongOrder = new Order("GOOGL", OrderSide.BUY, 150.0, 100, "a1");
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(wrongOrder));
    }

    @Test
    void nullOrderThrows() {
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(null));
    }

    @Test
    void emptyBookReturnsNull() {
        assertNull(book.getBestBid());
        assertNull(book.getBestAsk());
        assertTrue(book.isEmpty());
    }
}