package com.trading.agent;

import com.trading.engine.MatchingEngine;
import com.trading.model.Account;
import com.trading.model.Order;
import com.trading.model.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class AIAgentTest {

    private AIAgent agent;
    private Account account;

    @BeforeEach
    public void setUp() {
        // 每个测试方法运行前，初始化一次基础对象
        account = new Account("test_ai", 10000.0);
        
        // 注意：如果组长还没合并 MatchingEngine，这里你可以先 new 一个空的，或者用 Mock
        // 只要不真的调 decide() 引发网络请求，就不需要真实的引擎
        MatchingEngine engine = new MatchingEngine();

        agent = new AIAgent(
                "test_ai", 
                account, 
                engine, 
                Arrays.asList("AAPL"), 
                "sk-test_key_placeholder", // 测试用假 Key 就行
                "https://api.deepseek.com/chat/completions", 
                1000
        );
    }

    @Test
    public void testConstructorValidation() {
        // 测试传入非法参数时是否抛出异常
        assertThrows(IllegalArgumentException.class, () -> new AIAgent(null, account, null, Arrays.asList("AAPL"), "key", "url", 1000));
        assertThrows(IllegalArgumentException.class, () -> new AIAgent("id", null, null, Arrays.asList("AAPL"), "key", "url", 1000));
        assertThrows(IllegalArgumentException.class, () -> new AIAgent("id", account, null, null, "key", "url", 1000));
    }

    @Test
    public void testIsPriceSafe() {
        // 价格没变，安全
        assertTrue(agent.isPriceSafe(100.0, 100.0));
        
        // 价格上涨 4%，安全（在 ±5% 之内）
        assertTrue(agent.isPriceSafe(104.0, 100.0));
        
        // 价格上涨 6%，不安全
        assertFalse(agent.isPriceSafe(106.0, 100.0));
        
        // 价格下跌 6%，不安全
        assertFalse(agent.isPriceSafe(94.0, 100.0));
        
        // 上一次价格为 0，应默认安全
        assertTrue(agent.isPriceSafe(100.0, 0.0));
    }

    @Test
    public void testParseResponseValidBuy() {
        String json = "{\"action\": \"BUY\", \"price\": 105.5, \"quantity\": 10}";
        Order order = agent.parseResponse(json, "AAPL");
        
        assertNotNull(order);
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(105.5, order.getPrice(), 0.001);
        assertEquals(10, order.getQuantity());
    }

    @Test
    public void testParseResponseValidSell() {
        String json = "{\"action\": \"SELL\", \"price\": 120.0, \"quantity\": 5}";
        Order order = agent.parseResponse(json, "TSLA");
        
        assertNotNull(order);
        assertEquals(OrderSide.SELL, order.getSide());
        assertEquals(120.0, order.getPrice(), 0.001);
        assertEquals(5, order.getQuantity());
    }

    @Test
    public void testParseResponseHold() {
        // AI 建议 HOLD，应该返回 null，表示不操作
        String json = "{\"action\": \"HOLD\", \"price\": 0, \"quantity\": 0}";
        Order order = agent.parseResponse(json, "AAPL");
        assertNull(order);
    }

    @Test
    public void testParseResponseInvalidJson() {
        // AI 返回了乱七八糟的东西，解析应该返回 null 而不是抛异常
        String json = "Sorry, I cannot answer that.";
        Order order = agent.parseResponse(json, "AAPL");
        assertNull(order);
    }
}
