package com.trading.agent;

import com.trading.account.AccountManager;
import com.trading.engine.MatchingEngine;
import com.trading.model.Account;
import com.trading.model.MarketData;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class HumanAgentTest {

    @Test
    void createHumanAgent() {
        AccountManager am = new AccountManager();
        Account acc = new Account("h1", 100_000);
        am.addAccount(acc);
        MatchingEngine engine = new MatchingEngine(am);

        HumanAgent agent = new HumanAgent("h1", acc, engine);

        assertEquals("h1", agent.getAgentId());
        assertEquals("HUMAN", agent.getType());
        assertNotNull(agent.getAccount());
    }

    @Test
    void onMarketUpdateStoresData() {
        AccountManager am = new AccountManager();
        Account acc = new Account("h1", 100_000);
        am.addAccount(acc);
        MatchingEngine engine = new MatchingEngine(am);

        HumanAgent agent = new HumanAgent("h1", acc, engine);

        MarketData data = new MarketData("AAPL",
                149.0, 100, 151.0, 100, 150.0,
                Collections.emptyList(), Collections.emptyList());
        agent.onMarketUpdate(data);

        assertNotNull(agent.getLastMarketData());
        assertEquals("AAPL", agent.getLastMarketData().getSymbol());
    }

    @Test
    void nullConstructorArgThrows() {
        AccountManager am = new AccountManager();
        Account acc = new Account("h1", 100_000);
        am.addAccount(acc);
        MatchingEngine engine = new MatchingEngine(am);

        assertThrows(IllegalArgumentException.class,
                () -> new HumanAgent(null, acc, engine));
        assertThrows(IllegalArgumentException.class,
                () -> new HumanAgent("h1", null, engine));
        assertThrows(IllegalArgumentException.class,
                () -> new HumanAgent("h1", acc, null));
    }
}