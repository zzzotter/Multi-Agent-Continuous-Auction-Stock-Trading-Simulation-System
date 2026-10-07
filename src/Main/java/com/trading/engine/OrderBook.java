package com.trading.engine;

import com.trading.model.Order;
import com.trading.model.OrderSide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 订单簿：管理某一只股票的买卖挂单
 *
 * 核心：
 * - bids（买方队列）：价格从高到低，同价格时间早优先
 * - asks（卖方队列）：价格从低到高，同价格早优先
 *
 * 只负责排序和存取，不负责撮合。
 */
public class OrderBook {

    private final String symbol;

    /** 买方队列：价格降序，同价格时间升序 */
    private final PriorityQueue<Order> bids;

    /** 卖方队列：价格升序，同价格时间升序 */
    private final PriorityQueue<Order> asks;

    /**
     * 买方比较器：
     * 1. 价格高的优先（降序）
     * 2. 价格相同，时间早的优先（升序）
     */
    private static final Comparator<Order> BID_COMPARATOR = (o1, o2) -> {
        // 价格降序
        int priceCompare = Double.compare(o2.getPrice(), o1.getPrice());
        if (priceCompare != 0) {
            return priceCompare;
        }
        // 价格相同，时间升序
        return o1.getTimestamp().compareTo(o2.getTimestamp());
    };

    /**
     * 卖方比较器：
     * 1. 价格低的优先（升序）
     * 2. 价格相同，时间早的优先（升序）
     */
    private static final Comparator<Order> ASK_COMPARATOR = (o1, o2) -> {
        // 价格升序
        int priceCompare = Double.compare(o1.getPrice(), o2.getPrice());
        if (priceCompare != 0) {
            return priceCompare;
        }
        // 价格相同，时间升序
        return o1.getTimestamp().compareTo(o2.getTimestamp());
    };

    public OrderBook(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol cannot be null or empty");
        }
        this.symbol = symbol;
        this.bids = new PriorityQueue<>(BID_COMPARATOR);
        this.asks = new PriorityQueue<>(ASK_COMPARATOR);
    }

    // ========== 核心方法 ==========

    /**
     * 添加订单到对应队列
     */
    public void addOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order cannot be null");
        }
        if (!order.getSymbol().equals(symbol)) {
            throw new IllegalArgumentException(
                    "order symbol " + order.getSymbol() + " does not match book " + symbol);
        }
        if (order.isFilled()) {
            throw new IllegalArgumentException("cannot add filled order");
        }
        if (order.getSide() == OrderSide.BUY) {
            bids.offer(order);
        } else {
            asks.offer(order);
        }
    }

    /**
     * 移除订单（撤单或完全成交后调用）
     * 注意：PriorityQueue 的 remove 是 O(n)，但订单量小，可接受
     */
    public boolean removeOrder(Order order) {
        if (order.getSide() == OrderSide.BUY) {
            return bids.remove(order);
        } else {
            return asks.remove(order);
        }
    }

    /**
     * 获取最优买价（最高的买单）
     * 返回 null 如果买方队列为空
     */
    public Order getBestBid() {
        return bids.peek();
    }

    /**
     * 获取最优卖价（最低的卖单）
     * 返回 null 如果卖方队列为空
     */
    public Order getBestAsk() {
        return asks.peek();
    }

    /**
     * 获取盘口前 n 档买方
     */
    public List<Order> getTopBids(int n) {
        return peekTop(bids, n);
    }

    /**
     * 获取盘口前 n 档卖方
     */
    public List<Order> getTopAsks(int n) {
        return peekTop(asks, n);
    }

    /**
     * 从优先队列里"窥视"前 n 个元素，不破坏原队列
     */
    private List<Order> peekTop(PriorityQueue<Order> queue, int n) {
        List<Order> result = new ArrayList<>();
        List<Order> temp = new ArrayList<>();

        // 依次 poll 出来，加入结果，暂存到 temp
        for (int i = 0; i < n && !queue.isEmpty(); i++) {
            Order order = queue.poll();
            result.add(order);
            temp.add(order);
        }

        // 把 poll 出来的放回去
        queue.addAll(temp);
        return result;
    }

    // ========== 辅助方法 ==========

    public String getSymbol() {
        return symbol;
    }

    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    public int getBidCount() {
        return bids.size();
    }

    public int getAskCount() {
        return asks.size();
    }

    /**
     * 清空订单簿（用于测试或市场收盘）
     */
    public void clear() {
        bids.clear();
        asks.clear();
    }

    @Override
    public String toString() {
        return String.format("OrderBook[%s, bids=%d, asks=%d, bestBid=%s, bestAsk=%s]",
                symbol, bids.size(), asks.size(),
                getBestBid() == null ? "-" : String.format("%.2f", getBestBid().getPrice()),
                getBestAsk() == null ? "-" : String.format("%.2f", getBestAsk().getPrice()));
    }
}

