package com.trading.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单类：表示一笔买单或卖单
 *
 * 关键字段：
 * - symbol: 股票代码（如 AAPL）
 * - side: 买/卖
 * - price: 限价
 * - quantity: 数量
 * - remainingQuantity: 剩余未成交量（支持部分成交）
 * - timestamp: 下单时间（用于时间优先排序）
 */
public class Order {

    /** 全局订单 ID 生成器，线程安全 */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private final long orderId;
    private final String symbol;
    private final OrderSide side;
    private final double price;
    private final int quantity;
    private int remainingQuantity;
    private final LocalDateTime timestamp;
    private final String agentId;

    /**
     * 构造函数
     */
    public Order(String symbol, OrderSide side, double price, int quantity, String agentId) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol cannot be null or empty");
        }
        if (side == null) {
            throw new IllegalArgumentException("side cannot be null");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (agentId == null || agentId.isEmpty()) {
            throw new IllegalArgumentException("agentId cannot be null or empty");
        }

        this.orderId = ID_GENERATOR.getAndIncrement();
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timestamp = LocalDateTime.now();
        this.agentId = agentId;
    }

    // ========== Getters ==========

    public long getOrderId() { return orderId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getAgentId() { return agentId; }

    // ========== 业务方法 ==========

    /**
     * 是否已完全成交
     */
    public boolean isFilled() {
        return remainingQuantity == 0;
    }

    /**
     * 减少剩余数量（成交时调用）
     * @param amount 成交数量
     */
    public void reduceRemaining(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount > remainingQuantity) {
            throw new IllegalArgumentException(
                    "cannot reduce more than remaining: " + remainingQuantity);
        }
        this.remainingQuantity -= amount;
    }

    /**
     * 判断两个订单是否属于同一只股票
     */
    public boolean sameSymbol(Order other) {
        return this.symbol.equals(other.symbol);
    }

    @Override
    public String toString() {
        return String.format("Order[id=%d, %s %s %.2f x %d (remaining=%d), agent=%s]",
                orderId, side, symbol, price, quantity, remainingQuantity, agentId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return orderId == order.orderId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }
}