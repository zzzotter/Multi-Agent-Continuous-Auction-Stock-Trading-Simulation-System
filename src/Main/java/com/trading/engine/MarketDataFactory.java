package com.trading.engine;

import com.trading.model.Order;

import com.trading.model.MarketData;

public class MarketDataFactory {
    public static MarketData fromOrderBook(OrderBook book, Double lastPrice, int depth) {
        if (book == null) {
            throw new IllegalArgumentException("book cannot be null");
        }
        if (depth <= 0) {
            throw new IllegalArgumentException("depth must be positive");
        }

        Order bestBid = book.getBestBid();
        Order bestAsk = book.getBestAsk();

        Double bidPrice = bestBid == null ? null : bestBid.getPrice();
        Integer bidQty   = bestBid == null ? null : bestBid.getRemainingQuantity();
        Double askPrice = bestAsk == null ? null : bestAsk.getPrice();
        Integer askQty   = bestAsk == null ? null : bestAsk.getRemainingQuantity();

        return new MarketData(
                book.getSymbol(),
                bidPrice, bidQty,
                askPrice, askQty,
                lastPrice,
                book.getTopBids(depth),
                book.getTopAsks(depth)
        );
    }
}