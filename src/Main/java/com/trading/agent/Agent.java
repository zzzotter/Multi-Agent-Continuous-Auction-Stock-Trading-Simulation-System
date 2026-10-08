package com.trading.agent;

import com.trading.engine.MatchingEngine;
import com.trading.model.Account;
import com.trading.model.MarketData;

/**
 * 交易员抽象基类
 *
 * 两种子类：
 * - HumanAgent：手动交易
 * - AIAgent：自动决策
 */
public abstract class Agent {

    protected final String agentId;
    protected final Account account;
    protected final MatchingEngine engine;

    public Agent(String agentId, Account account, MatchingEngine engine) {
        if (agentId == null || agentId.isEmpty()) {
            throw new IllegalArgumentException("agentId cannot be null or empty");
        }
        if (account == null) {
            throw new IllegalArgumentException("account cannot be null");
        }
        if (engine == null) {
            throw new IllegalArgumentException("engine cannot be null");
        }
        this.agentId = agentId;
        this.account = account;
        this.engine = engine;
    }

    public String getAgentId() { return agentId; }
    public Account getAccount() { return account; }

    /**
     * 收到行情更新（由定时器调用）
     * 子类实现自己的行为：
     * - HumanAgent：记录状态，等用户操作
     * - AIAgent：触发决策
     */
    public abstract void onMarketUpdate(MarketData data);

    /**
     * 类型名称（用于日志、报表）
     */
    public abstract String getType();

    @Override
    public String toString() {
        return String.format("Agent[%s (%s), cash=%.2f]",
                agentId, getType(), account.getCash());
    }
}
