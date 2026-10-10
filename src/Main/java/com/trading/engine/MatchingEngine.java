package com.trading.engine;

import com.trading.account.AccountManager;
import com.trading.engine.OrderBook;
import com.trading.model.Account;
import com.trading.model.Order;
import com.trading.model.OrderSide;
import com.trading.model.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



/**
 * 撮合引擎：系统的核心
 *
 * 线程安全：submitOrder 是 synchronized
 */
public class MatchingEngine {
    /** 所有成交记录（用于 GUI 展示、统计分析） */
    private final List<Trade> allTrades = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** 每只股票一个订单簿 */
    private final Map<String, OrderBook> orderBooks;

    /** 账户管理器：通过 agentId 查账户 */
    private final AccountManager accountManager;

    public MatchingEngine(AccountManager accountManager) {
        if (accountManager == null) {
            throw new IllegalArgumentException("accountManager cannot be null");
        }
        this.accountManager = accountManager;
        this.orderBooks = new ConcurrentHashMap<>();
    }

    public void registerSymbol(String symbol) {
        orderBooks.putIfAbsent(symbol, new OrderBook(symbol));
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }

    // ============================================================
    // 核心方法：提交订单并撮合
    // ============================================================

    public synchronized List<Trade> submitOrder(Order order) {
        if (order == null) throw new IllegalArgumentException("order cannot be null");

        OrderBook book = orderBooks.get(order.getSymbol());
        if (book == null) {
            throw new IllegalArgumentException(
                    "order book not registered for symbol: " + order.getSymbol());
        }

        Account submitterAccount = accountManager.getAccount(order.getAgentId());
        if (submitterAccount == null) {
            throw new IllegalArgumentException(
                    "no account found for agent: " + order.getAgentId());
        }

        // 账户校验
        validateAccount(order, submitterAccount);

        List<Trade> trades = new ArrayList<>();

        if (order.getSide() == OrderSide.BUY) {
            matchBuyOrder(order, submitterAccount, book, trades);
        } else {
            matchSellOrder(order, submitterAccount, book, trades);
        }

        // 剩余数量挂单
        if (order.getRemainingQuantity() > 0) {
            book.addOrder(order);
        }
        // 记录所有成交
        allTrades.addAll(trades);
        return trades;
    }
    /**
     * 获取所有历史成交记录（返回副本，线程安全）
     */
    public List<Trade> getAllTrades() {
        return new ArrayList<>(allTrades);
    }

    /**
     * 获取某只股票的最新成交价（没有成交则返回 null）
     */
    public Double getLastPrice(String symbol) {
        // 从后往前找，第一个匹配 symbol 的
        for (int i = allTrades.size() - 1; i >= 0; i--) {
            Trade t = allTrades.get(i);
            if (t.getSymbol().equals(symbol)) {
                return t.getPrice();
            }
        }
        return null;
    }
    // ============================================================
    // 买单撮合：匹配 asks
    // ============================================================

    private void matchBuyOrder(Order buyOrder, Account buyAccount,
                               OrderBook book, List<Trade> trades) {
        while (buyOrder.getRemainingQuantity() > 0) {
            Order bestAsk = book.getBestAsk();
            if (bestAsk == null) break;
            if (buyOrder.getPrice() < bestAsk.getPrice()) break;

            Account sellAccount = accountManager.getAccount(bestAsk.getAgentId());
            if (sellAccount == null) {
                throw new IllegalStateException(
                        "no account for seller agent: " + bestAsk.getAgentId());
            }

            double tradePrice = bestAsk.getPrice();
            int tradeQty = Math.min(buyOrder.getRemainingQuantity(),
                    bestAsk.getRemainingQuantity());

            executeTrade(buyOrder, buyAccount, bestAsk, sellAccount,
                    book.getSymbol(), tradePrice, tradeQty, trades);

            if (bestAsk.isFilled()) {
                book.removeOrder(bestAsk);
            }
        }
    }

    // ============================================================
    // 卖单撮合：匹配 bids
    // ============================================================

    private void matchSellOrder(Order sellOrder, Account sellAccount,
                                OrderBook book, List<Trade> trades) {
        while (sellOrder.getRemainingQuantity() > 0) {
            Order bestBid = book.getBestBid();
            if (bestBid == null) break;
            if (sellOrder.getPrice() > bestBid.getPrice()) break;

            Account buyAccount = accountManager.getAccount(bestBid.getAgentId());
            if (buyAccount == null) {
                throw new IllegalStateException(
                        "no account for buyer agent: " + bestBid.getAgentId());
            }

            double tradePrice = bestBid.getPrice();
            int tradeQty = Math.min(sellOrder.getRemainingQuantity(),
                    bestBid.getRemainingQuantity());

            executeTrade(bestBid, buyAccount, sellOrder, sellAccount,
                    book.getSymbol(), tradePrice, tradeQty, trades);

            if (bestBid.isFilled()) {
                book.removeOrder(bestBid);
            }
        }
    }

    // ============================================================
    // 执行一次成交
    // ============================================================

    private void executeTrade(Order buyOrder, Account buyAccount,
                              Order sellOrder, Account sellAccount,
                              String symbol, double price, int qty,
                              List<Trade> trades) {
        // 1. 更新订单剩余数量
        buyOrder.reduceRemaining(qty);
        sellOrder.reduceRemaining(qty);

        // 2. 生成 Trade
        Trade trade = new Trade(symbol, price, qty, buyOrder, sellOrder);
        trades.add(trade);

        // 3. 更新买方账户：扣钱、加仓
        buyAccount.withdraw(price * qty);
        buyAccount.addHolding(symbol, qty);

        // 4. 更新卖方账户：加钱、减仓
        sellAccount.deposit(price * qty);
        sellAccount.removeHolding(symbol, qty);
    }

    // ============================================================
    // 账户校验
    // ============================================================

    private void validateAccount(Order order, Account account) {
        if (order.getSide() == OrderSide.BUY) {
            double required = order.getPrice() * order.getQuantity();
            if (account.getCash() < required) {
                throw new IllegalArgumentException(
                        String.format("insufficient cash: need %.2f, have %.2f",
                                required, account.getCash()));
            }
        } else {
            int holding = account.getHolding(order.getSymbol());
            if (holding < order.getQuantity()) {
                throw new IllegalArgumentException(
                        String.format("insufficient holding of %s: need %d, have %d",
                                order.getSymbol(), order.getQuantity(), holding));
            }
        }
    }
}