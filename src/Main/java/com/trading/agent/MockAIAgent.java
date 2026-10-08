package com.trading.agent;

import com.trading.engine.MatchingEngine;
import com.trading.model.Account;
import com.trading.model.MarketData;
import com.trading.model.Order;
import com.trading.model.OrderSide;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 临时的 Mock AI 交易员：用随机策略决策
 * 等真正的 AIAgent（调 LLM）就绪后替换
 */
public class MockAIAgent extends Agent {

    private final List<String> symbols;
    private final Random random = new Random();
    private final AtomicLong lastDecisionTime = new AtomicLong(0);
    private final long decisionIntervalMs;

    public MockAIAgent(String agentId, Account account, MatchingEngine engine,
                       List<String> symbols, long decisionIntervalMs) {
        super(agentId, account, engine);
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols cannot be empty");
        }
        this.symbols = symbols;
        this.decisionIntervalMs = decisionIntervalMs;
    }

    @Override
    public void onMarketUpdate(MarketData data) {
        long now = System.currentTimeMillis();
        if (now - lastDecisionTime.get() < decisionIntervalMs) {
            return;  // 限流
        }
        lastDecisionTime.set(now);

        // 随机决策：70% HOLD，15% BUY，15% SELL
        int roll = random.nextInt(100);
        if (roll < 70) return;

        String symbol = data.getSymbol();
        Double lastPrice = data.getLastPrice();
        if (lastPrice == null) lastPrice = 100.0;

        // 价格在 lastPrice ±2% 内
        double price = lastPrice * (0.98 + random.nextDouble() * 0.04);
        price = Math.round(price * 100.0) / 100.0;
        int qty = 10 + random.nextInt(50);

        OrderSide side = (roll < 85) ? OrderSide.BUY : OrderSide.SELL;

        // 简单校验：现金/持仓够不够
        if (side == OrderSide.BUY) {
            if (account.getCash() < price * qty) return;
        } else {
            if (account.getHolding(symbol) < qty) return;
        }

        try {
            Order order = new Order(symbol, side, price, qty, agentId);
            engine.submitOrder(order);
        } catch (Exception e) {
            // 失败就忽略，不能崩
        }
    }

    @Override
    public String getType() {
        return "MOCK_AI";
    }
}