package com.trading.agent;

import com.trading.engine.MatchingEngine;
import com.trading.engine.OrderBook;
import com.trading.model.Account;
import com.trading.model.Order;
import com.trading.model.OrderSide;
import com.trading.model.Trade;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AIAgent {
    private final String agentId;
    private final Account account;
    private final MatchingEngine engine;
    private final List<String> symbols;
    private final String apiKey;
    private final String apiEndpoint;
    private final long rateLimitMs;
    private long lastCallTime = 0; // 记录上次调用时间，用于限流

    public AIAgent(String agentId, Account account, MatchingEngine engine,
                   List<String> symbols, String apiKey,
                   String apiEndpoint, long rateLimitMs) {
        if (agentId == null || agentId.trim().isEmpty()) throw new IllegalArgumentException("agentId cannot be empty");
        if (account == null) throw new IllegalArgumentException("account cannot be null");
        if (engine == null) throw new IllegalArgumentException("engine cannot be null");
        if (symbols == null || symbols.isEmpty()) throw new IllegalArgumentException("symbols cannot be empty");
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("apiKey cannot be empty");
        if (apiEndpoint == null || apiEndpoint.trim().isEmpty()) throw new IllegalArgumentException("apiEndpoint cannot be empty");

        this.agentId = agentId;
        this.account = account;
        this.engine = engine;
        this.symbols = symbols;
        this.apiKey = apiKey;
        this.apiEndpoint = apiEndpoint;
        this.rateLimitMs = rateLimitMs;
    }

    public String getAgentId() { return agentId; }
    public Account getAccount() { return account; }

    // 核心：触发一次决策循环
    public void decide() throws Exception {
        for (String symbol : symbols) {
            try {
                // 1. 限流校验
                long now = System.currentTimeMillis();
                if (now - lastCallTime < rateLimitMs) {
                    Thread.sleep(rateLimitMs - (now - lastCallTime));
                }
                lastCallTime = System.currentTimeMillis();

                // 2. 获取行情
                OrderBook orderBook = engine.getOrderBook(symbol);
                if (orderBook == null) {
                    System.err.println("[" + agentId + "] " + symbol + " 订单簿不存在，跳过");
                    continue;
                }
                Order bestBidOrder = orderBook.getBestBid();
                Order bestAskOrder = orderBook.getBestAsk();
                Double bestBid = bestBidOrder == null ? null : bestBidOrder.getPrice();
                Double bestAsk = bestAskOrder == null ? null : bestAskOrder.getPrice();
                double lastPrice = (bestBid != null && bestAsk != null) ? (bestBid + bestAsk) / 2.0
                        : (bestBid != null ? bestBid : (bestAsk != null ? bestAsk : 100.0)); // 兜底价格

                // 3. 构建 prompt 并调用 LLM
                String prompt = buildPrompt(symbol);
                String jsonResponse = callLLM(prompt);

                // 4. 解析响应
                Order order = parseResponse(jsonResponse, symbol);
                if (order == null) continue;

                // 5. 安全校验
                if (!isPriceSafe(order.getPrice(), lastPrice)) {
                    System.err.println("[" + agentId + "] " + symbol + " 价格波动过大，拒绝下单: " + order.getPrice());
                    continue;
                }
                
                // 校验数量
                if (order.getSide() == OrderSide.BUY && order.getPrice() * order.getQuantity() > account.getCash()) {
                    System.err.println("[" + agentId + "] 现金不足，拒绝买单");
                    continue;
                }
                if (order.getSide() == OrderSide.SELL && order.getQuantity() > account.getHolding(symbol)) {
                    System.err.println("[" + agentId + "] 持仓不足，拒绝卖单");
                    continue;
                }

                // 6. 提交订单
                List<Trade> trades = engine.submitOrder(order);
                System.out.println("✅ [" + agentId + "] " + symbol + " 下单成功: " + order.getSide() + " " + order.getQuantity() + " @ " + order.getPrice());

            } catch (Exception e) {
                // 核心要求：出错不能导致定时器挂掉
                System.err.println("❌ [" + agentId + "] " + symbol + " 决策出错: " + e.getMessage());
                // 回退到 HOLD，什么都不做
            }
        }
    }

    // 构建 prompt
    private String buildPrompt(String symbol) {
        int holding = account.getHolding(symbol);
        double cash = account.getCash();
        
        return "你是一个量化交易 AI，请根据以下信息对股票 " + symbol + " 做出交易决策。\n" +
                "当前账户可用现金: " + cash + " 元\n" +
                "当前持仓数量: " + holding + " 股\n" +
                "请严格按照以下 JSON 格式返回，不要包含任何其他解释性文字：\n" +
                "{\"action\": \"BUY\" | \"SELL\" | \"HOLD\", \"price\": 100.0, \"quantity\": 10}\n" +
                "注意：如果选择 HOLD，price 和 quantity 可以随意填写。";
    }

    // 调真实 LLM API (使用 Java 11 HttpClient)
    private String callLLM(String prompt) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        // 简易构造 JSON 请求体，注意转义 prompt 中的双引号
        String requestBody = "{\"model\": \"deepseek-chat\", \"messages\": [{\"role\": \"user\", \"content\": \"" 
                + prompt.replace("\"", "\\\"").replace("\n", "\\n") + "\"}], \"temperature\": 0.1}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        System.out.println("⏳ [" + agentId + "] 正在请求 DeepSeek API...");
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("API 请求失败，状态码: " + response.statusCode() + "，响应: " + response.body());
        }
        System.out.println("【真实API】收到回复！");
        return response.body();
    }

    // 解析 JSON 响应 (这里用正则做最简单的提取)
    private Order parseResponse(String json, String symbol) {
        try {
            // 提取 action
            Pattern actionPattern = Pattern.compile("\"action\"\\s*:\\s*\"(BUY|SELL|HOLD)\"");
            Matcher actionMatcher = actionPattern.matcher(json);
            if (!actionMatcher.find()) return null;
            String actionStr = actionMatcher.group(1);
            if ("HOLD".equals(actionStr)) return null;

            // 提取 price
            Pattern pricePattern = Pattern.compile("\"price\"\\s*:\\s*([0-9.]+)");
            Matcher priceMatcher = pricePattern.matcher(json);
            if (!priceMatcher.find()) return null;
            double price = Double.parseDouble(priceMatcher.group(1));

            // 提取 quantity
            Pattern qtyPattern = Pattern.compile("\"quantity\"\\s*:\\s*([0-9]+)");
            Matcher qtyMatcher = qtyPattern.matcher(json);
            if (!qtyMatcher.find()) return null;
            int quantity = Integer.parseInt(qtyMatcher.group(1));

            if (quantity <= 0) return null;

            OrderSide side = OrderSide.valueOf(actionStr);
            return new Order(symbol, side, price, quantity, agentId);
        } catch (Exception e) {
            System.err.println("解析 JSON 失败: " + e.getMessage() + "，原文: " + json);
            return null;
        }
    }

    // 安全校验：价格在 lastPrice 的 ±5% 内
    private boolean isPriceSafe(double price, double lastPrice) {
        if (lastPrice <= 0) return true;
        double changePercent = Math.abs(price - lastPrice) / lastPrice;
        return changePercent <= 0.05;
    }
}
