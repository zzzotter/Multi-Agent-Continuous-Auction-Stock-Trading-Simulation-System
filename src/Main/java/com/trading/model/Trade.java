package com.trading.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 成交记录：一笔撮合成功的结果
 *
 * 与 Order 的区别：
 * - Order: 下单意图（可能部分成交或未成交）
 * - Trade: 实际成交（一定成交了，价格数量确定）
 *
 * 一次撮合可能产生多条 Trade（买方订单可能吃多个卖方订单）
 */
public class Trade {

    /** 全局成交 ID 生成器，线程安全 */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private final long tradeId;
    private final String symbol;
    private final double price;
    private final int quantity;
    private final long buyOrderId;
    private final long sellOrderId;
    private final String buyerAgentId;
    private final String sellerAgentId;
    private final LocalDateTime timestamp;

    /**
     * 构造函数：由撮合引擎在成交时调用
     *
     * @param symbol         股票代码
     * @param price          成交价
     * @param quantity       成交量
     * @param buyOrder       买方订单
     * @param sellOrder      卖方订单
     */
    public Trade(String symbol, double price, int quantity,
                 Order buyOrder, Order sellOrder) {
        // 参数校验
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol cannot be null or empty");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (buyOrder == null || sellOrder == null) {
            throw new IllegalArgumentException("orders cannot be null");
        }
        // 语义校验：买方订单必须是 BUY，卖方订单必须是 SELL
        if (buyOrder.getSide() != OrderSide.BUY) {
            throw new IllegalArgumentException("buyOrder must be a BUY order");
        }
        if (sellOrder.getSide() != OrderSide.SELL) {
            throw new IllegalArgumentException("sellOrder must be a SELL order");
        }

        this.tradeId = ID_GENERATOR.getAndIncrement();
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.buyOrderId = buyOrder.getOrderId();
        this.sellOrderId = sellOrder.getOrderId();
        this.buyerAgentId = buyOrder.getAgentId();
        this.sellerAgentId = sellOrder.getAgentId();
        this.timestamp = LocalDateTime.now();
    }

    // ========== Getters ==========

    public long getTradeId() { return tradeId; }
    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public long getBuyOrderId() { return buyOrderId; }
    public long getSellOrderId() { return sellOrderId; }
    public String getBuyerAgentId() { return buyerAgentId; }
    public String getSellerAgentId() { return sellerAgentId; }
    public LocalDateTime getTimestamp() { return timestamp; }

    // ========== 业务方法 ==========

    /**
     * 计算成交金额（用于账户资金更新）
     */
    public double getAmount() {
        return price * quantity;
    }

    /**
     * 判断某个 agent 是否参与了这笔成交
     */
    public boolean involves(String agentId) {
        return buyerAgentId.equals(agentId) || sellerAgentId.equals(agentId);
    }

    @Override
    public String toString() {
        return String.format(
                "Trade[id=%d, %s %.2f x %d, buyer=%s, seller=%s]",
                tradeId, symbol, price, quantity, buyerAgentId, sellerAgentId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return tradeId == trade.tradeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId);
    }
}