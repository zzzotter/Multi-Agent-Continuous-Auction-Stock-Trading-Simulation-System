package com.trading.model;

import java.util.HashMap;
import java.util.Map;

public class Account {
    private final String agentId;
    private double cash;
    private final Map<String, Integer> holdings;

    public Account(String agentId, double initialCash) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new IllegalArgumentException("agentId cannot be null or empty");
        }
        if (initialCash < 0) {
            throw new IllegalArgumentException("initialCash cannot be negative");
        }
        this.agentId = agentId;
        this.cash = initialCash;
        this.holdings = new HashMap<>();
    }

    // MatchingEngine 必须的方法
    public synchronized String getAgentId() {
        return agentId;
    }

    public synchronized double getCash() {
        return cash;
    }

    public synchronized int getHolding(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol cannot be null or empty");
        }
        return holdings.getOrDefault(symbol, 0);
    }

    public synchronized void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("deposit amount must be positive");
        }
        cash += amount;
    }

    public synchronized void withdraw(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("withdraw amount must be positive");
        }
        if (cash < amount) {
            throw new IllegalArgumentException("insufficient cash: available=" + cash + ", required=" + amount);
        }
        cash -= amount;
    }

    public synchronized void addHolding(String symbol, int qty) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol cannot be null or empty");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        holdings.put(symbol, holdings.getOrDefault(symbol, 0) + qty);
    }

    public synchronized void removeHolding(String symbol, int qty) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol cannot be null or empty");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        int current = holdings.getOrDefault(symbol, 0);
        if (current < qty) {
            throw new IllegalArgumentException("insufficient holding for " + symbol + ": available=" + current + ", required=" + qty);
        }
        if (current == qty) {
            holdings.remove(symbol);
        } else {
            holdings.put(symbol, current - qty);
        }
    }

    // 辅助方法（方便绩效计算和查看持仓）
    public synchronized Map<String, Integer> getHoldings() {
        return new HashMap<>(holdings); // 返回副本，避免外部修改
    }

    public synchronized double getTotalEquity(Map<String, Double> prices) {
        if (prices == null) {
            throw new IllegalArgumentException("prices cannot be null");
        }
        double total = cash;
        for (Map.Entry<String, Integer> entry : holdings.entrySet()) {
            String symbol = entry.getKey();
            int qty = entry.getValue();
            Double price = prices.get(symbol);
            if (price != null) {
                total += qty * price;
            }
        }
        return total;
    }

    @Override
    public synchronized String toString() {
        return "Account{" +
                "agentId='" + agentId + '\'' +
                ", cash=" + cash +
                ", holdings=" + holdings +
                '}';
    }
}
