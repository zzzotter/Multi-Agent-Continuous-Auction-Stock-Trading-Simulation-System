package com.trading;

import com.trading.account.AccountManager;
import com.trading.agent.Agent;
import com.trading.agent.HumanAgent;
import com.trading.agent.MockAIAgent;
import com.trading.engine.MatchingEngine;
import com.trading.engine.MarketDataFactory;
import com.trading.engine.OrderBook;
import com.trading.model.Account;
import com.trading.model.MarketData;
import com.trading.model.Order;
import com.trading.model.OrderSide;
import com.trading.model.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 模拟运行器：控制台版
 * 启动多 Agent 模拟交易，观察价格发现
 */
public class SimulationRunner {

    private static final List<String> SYMBOLS = List.of("AAPL", "GOOGL", "TSLA");
    private static final Map<String, Double> INITIAL_PRICES = Map.of(
            "AAPL", 175.50,
            "GOOGL", 141.80,
            "TSLA", 245.30
    );

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Multi-Agent Trading Simulation ===\n");

        // 1. 初始化
        AccountManager accountManager = new AccountManager();
        MatchingEngine engine = new MatchingEngine(accountManager);

        for (String symbol : SYMBOLS) {
            engine.registerSymbol(symbol);
        }

        // 2. 创建 Agent
        List<Agent> agents = new ArrayList<>();

        // 人类 Agent
        for (int i = 1; i <= 2; i++) {
            String id = "human" + i;
            Account acc = new Account(id, 100_000.0);
            // 给人类 Agent 一点初始持仓，方便卖
            for (String s : SYMBOLS) {
                acc.addHolding(s, 100);
            }
            accountManager.addAccount(acc);
            agents.add(new HumanAgent(id, acc, engine));
        }

        // AI Agent（Mock）
        for (int i = 1; i <= 3; i++) {
            String id = "ai" + i;
            Account acc = new Account(id, 50_000.0);
            for (String s : SYMBOLS) {
                acc.addHolding(s, 50);
            }
            accountManager.addAccount(acc);
            agents.add(new MockAIAgent(id, acc, engine, SYMBOLS, 500));
        }

        // 3. 为了市场启动，先注入一批初始挂单（否则没人交易）
        seedInitialLiquidity(engine, accountManager);

        // 4. 模拟循环
        int rounds = 20;
        Random random = new Random();

        for (int round = 1; round <= rounds; round++) {
            System.out.println("========== Round " + round + " ==========");

            // 随机选一个 HumanAgent 下单
            for (Agent agent : agents) {
                if (agent instanceof HumanAgent) {
                    randomlySubmitHumanOrder(agent, engine, random);
                }
            }

            // 触发所有 Agent 的 onMarketUpdate
            for (String symbol : SYMBOLS) {
                OrderBook book = engine.getOrderBook(symbol);
                Double lastPrice = INITIAL_PRICES.get(symbol);
                MarketData md = MarketDataFactory.fromOrderBook(book, lastPrice, 5);
                for (Agent agent : agents) {
                    agent.onMarketUpdate(md);
                }
            }

            // 打印订单簿
            printOrderBooks(engine);

            Thread.sleep(300);
        }

        // 5. 收盘，打印结果
        System.out.println("\n========== Market Close ==========");
        printFinalRanking(agents);
    }

    // ============================================================

    private static void seedInitialLiquidity(MatchingEngine engine, AccountManager am) {
        // 用人类 agent 挂一些初始买卖单，让市场"动"起来
        Random rand = new Random(42);
        for (String symbol : SYMBOLS) {
            double price = INITIAL_PRICES.get(symbol);
            Account acc = am.getAccount("human1");

            // 挂 5 个买单，5 个卖单
            for (int i = 0; i < 5; i++) {
                double bidPrice = price - 1 - i * 0.5;
                double askPrice = price + 1 + i * 0.5;
                try {
                    engine.submitOrder(new Order(symbol, OrderSide.BUY,
                            bidPrice, 50, "human1"));
                    engine.submitOrder(new Order(symbol, OrderSide.SELL,
                            askPrice, 50, "human1"));
                } catch (Exception e) {
                    // 余额不够就跳过
                }
            }
        }
    }

    private static void randomlySubmitHumanOrder(Agent agent, MatchingEngine engine, Random random) {
        if (random.nextInt(100) < 60) return;  // 60% 不下单

        String symbol = SYMBOLS.get(random.nextInt(SYMBOLS.size()));
        Account acc = agent.getAccount();
        double lastPrice = INITIAL_PRICES.get(symbol);

        OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        double price = lastPrice * (0.98 + random.nextDouble() * 0.04);
        price = Math.round(price * 100.0) / 100.0;
        int qty = 10 + random.nextInt(30);

        try {
            engine.submitOrder(new Order(symbol, side, price, qty, agent.getAgentId()));
        } catch (Exception e) {
            // 忽略
        }
    }

    private static void printOrderBooks(MatchingEngine engine) {
        for (String symbol : SYMBOLS) {
            OrderBook book = engine.getOrderBook(symbol);
            System.out.println("  " + book);
        }
    }

    private static void printFinalRanking(List<Agent> agents) {
        System.out.println("\n--- Final Ranking ---");
        List<Agent> sorted = new ArrayList<>(agents);
        sorted.sort((a, b) -> Double.compare(
                b.getAccount().getCash(), a.getAccount().getCash()));

        for (int i = 0; i < sorted.size(); i++) {
            Agent a = sorted.get(i);
            System.out.printf("  #%d %s (%s) cash=%.2f holdings=%s%n",
                    i + 1, a.getAgentId(), a.getType(),
                    a.getAccount().getCash(), a.getAccount().getHoldings());
        }
    }
}

