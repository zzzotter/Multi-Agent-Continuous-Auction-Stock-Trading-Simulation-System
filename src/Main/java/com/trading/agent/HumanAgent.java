package com.trading.agent;

import com.trading.engine.MatchingEngine;
import com.trading.model.Account;
import com.trading.model.MarketData;

/**
 * 人类交易员
 *
 * 不自动决策。GUI 提交订单时直接调用 submitOrder。
 * onMarketUpdate 只更新内部"看到的行情"（供 GUI 展示）。
 */
public class HumanAgent extends Agent {

    // 最近一次看到的行情（供 GUI 查询）
    private volatile MarketData lastMarketData;

    public HumanAgent(String agentId, Account account, MatchingEngine engine) {
        super(agentId, account, engine);
    }

    @Override
    public void onMarketUpdate(MarketData data) {
        // 人类交易员只是记录，不做决策
        this.lastMarketData = data;
    }

    @Override
    public String getType() {
        return "HUMAN";
    }

    public MarketData getLastMarketData() {
        return lastMarketData;
    }
}