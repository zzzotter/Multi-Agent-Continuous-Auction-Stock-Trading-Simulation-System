package com.trading.engine;

import com.trading.account.AccountManager;
import com.trading.model.Account;
import com.trading.model.Order;
import com.trading.model.OrderSide;
import com.trading.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private MatchingEngine engine;
    private AccountManager accountManager;
    private Account buyer;
    private Account seller;

    @BeforeEach
    void setUp() {
        accountManager = new AccountManager();
        buyer = new Account("buyer1", 100_000.0);
        seller = new Account("seller1", 100_000.0);
        accountManager.addAccount(buyer);
        accountManager.addAccount(seller);

        engine = new MatchingEngine(accountManager);
        engine.registerSymbol("AAPL");

        // 给卖方初始持仓
        seller.addHolding("AAPL", 1000);
    }

    // ========== 场景 1：完全成交 ==========

    @Test
    void simpleMatch() {
        Order buyOrder = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer1");
        Order sellOrder = new Order("AAPL", OrderSide.SELL, 149.0, 100, "seller1");

        engine.submitOrder(sellOrder);  // 先挂卖单
        List<Trade> trades = engine.submitOrder(buyOrder);  // 买单进来

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(149.0, trade.getPrice());  // 以卖方挂单价成交
        assertEquals(100, trade.getQuantity());

        // 买方：花了 14900，持仓 +100
        assertEquals(100_000.0 - 149 * 100, buyer.getCash(), 0.001);
        assertEquals(100, buyer.getHolding("AAPL"));

        // 卖方：收了 14900，持仓 -100
        assertEquals(100_000.0 + 149 * 100, seller.getCash(), 0.001);
        assertEquals(900, seller.getHolding("AAPL"));
    }

    // ========== 场景 2：价格不匹配，挂单 ==========

    @Test
    void noMatchWhenPriceTooLow() {
        Order sellOrder = new Order("AAPL", OrderSide.SELL, 150.0, 100, "seller1");
        engine.submitOrder(sellOrder);

        Order buyOrder = new Order("AAPL", OrderSide.BUY, 149.0, 100, "buyer1");
        List<Trade> trades = engine.submitOrder(buyOrder);

        assertTrue(trades.isEmpty());
        // 买单挂进订单簿
        assertEquals(1, engine.getOrderBook("AAPL").getBidCount());
        assertEquals(1, engine.getOrderBook("AAPL").getAskCount());
    }

    // ========== 场景 3：部分成交 ==========

    @Test
    void partialFill() {
        Order sellOrder = new Order("AAPL", OrderSide.SELL, 149.0, 60, "seller1");
        engine.submitOrder(sellOrder);

        Order buyOrder = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer1");
        List<Trade> trades = engine.submitOrder(buyOrder);

        assertEquals(1, trades.size());
        assertEquals(60, trades.get(0).getQuantity());
        assertEquals(60, buyOrder.getQuantity() - buyOrder.getRemainingQuantity());
        assertEquals(40, buyOrder.getRemainingQuantity());
        // 买单剩 40 挂在订单簿
        assertEquals(1, engine.getOrderBook("AAPL").getBidCount());
    }

    // ========== 场景 4：一笔买单吃多个卖单 ==========

    @Test
    void buyOrderEatsMultipleAsks() {
        engine.submitOrder(new Order("AAPL", OrderSide.SELL, 149.0, 40, "seller1"));
        engine.submitOrder(new Order("AAPL", OrderSide.SELL, 150.0, 50, "seller1"));
        engine.submitOrder(new Order("AAPL", OrderSide.SELL, 151.0, 60, "seller1"));

        Order buyOrder = new Order("AAPL", OrderSide.BUY, 150.0, 100, "buyer1");
        List<Trade> trades = engine.submitOrder(buyOrder);

        // 应该成交 2 笔：40 @149, 50 @150
        assertEquals(2, trades.size());
        assertEquals(149.0, trades.get(0).getPrice());
        assertEquals(40, trades.get(0).getQuantity());
        assertEquals(150.0, trades.get(1).getPrice());
        assertEquals(50, trades.get(1).getQuantity());
        // 买单剩 10，挂在订单簿
        assertEquals(10, buyOrder.getRemainingQuantity());
        assertEquals(1, engine.getOrderBook("AAPL").getBidCount());
    }

    // ========== 场景 5：现金不足 ==========

    @Test
    void insufficientCashThrows() {
        Account poorBuyer = new Account("poor", 100.0);  // 只有 100 块
        accountManager.addAccount(poorBuyer);

        Order buyOrder = new Order("AAPL", OrderSide.BUY, 150.0, 100, "poor");  // 需要 15000
        assertThrows(IllegalArgumentException.class, () -> engine.submitOrder(buyOrder));
    }

    // ========== 场景 6：持仓不足 ==========

    @Test
    void insufficientHoldingThrows() {
        Account noHoldingSeller = new Account("noHolding", 100_000.0);
        accountManager.addAccount(noHoldingSeller);
        // 故意不给持仓

        Order sellOrder = new Order("AAPL", OrderSide.SELL, 150.0, 100, "noHolding");
        assertThrows(IllegalArgumentException.class, () -> engine.submitOrder(sellOrder));
    }
}
