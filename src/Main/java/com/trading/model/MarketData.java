package com.trading.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 行情快照：某一时刻某只股票的市场状态
 *
 * 从 OrderBook 抽取，供 Agent（尤其是 AIAgent）做决策
 * 不可变对象：一旦创建，内容不可改
 */
public class MarketData {

    private final String symbol;
    private final Double bestBid;
    private final Integer bestBidQty;
    private final Double bestAsk;
    private final Integer bestAskQty;
    private final Double lastPrice;
    private final List<Order> topBids;
    private final List<Order> topAsks;

    public MarketData(String symbol,
                      Double bestBid, Integer bestBidQty,
                      Double bestAsk, Integer bestAskQty,
                      Double lastPrice,
                      List<Order> topBids, List<Order> topAsks) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol cannot be null or empty");
        }
        this.symbol = symbol;
        this.bestBid = bestBid;
        this.bestBidQty = bestBidQty;
        this.bestAsk = bestAsk;
        this.bestAskQty = bestAskQty;
        this.lastPrice = lastPrice;
        this.topBids = topBids == null ? Collections.emptyList()
                : new ArrayList<>(topBids);
        this.topAsks = topAsks == null ? Collections.emptyList()
                : new ArrayList<>(topAsks);
    }

    // ========== Getters ==========

    public String getSymbol() { return symbol; }
    public Double getBestBid() { return bestBid; }
    public Integer getBestBidQty() { return bestBidQty; }
    public Double getBestAsk() { return bestAsk; }
    public Integer getBestAskQty() { return bestAskQty; }
    public Double getLastPrice() { return lastPrice; }
    public List<Order> getTopBids() { return new ArrayList<>(topBids); }
    public List<Order> getTopAsks() { return new ArrayList<>(topAsks); }

    // ========== 辅助 ==========

    /** 买卖价差，市场流动性指标 */
    public Double getSpread() {
        if (bestBid == null || bestAsk == null) return null;
        return bestAsk - bestBid;
    }

    /** 中间价 */
    public Double getMidPrice() {
        if (bestBid == null || bestAsk == null) return null;
        return (bestBid + bestAsk) / 2.0;
    }

    @Override
    public String toString() {
        return String.format(
                "MarketData[%s, bid=%s@%s, ask=%s@%s, last=%s]",
                symbol, bestBid, bestBidQty, bestAsk, bestAskQty, lastPrice);
    }
}