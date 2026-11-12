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
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AIAgent extends Agent {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<String> symbols;
    private final String apiKey;
    private final String apiEndpoint;
    private final long rateLimitMs;
    private long lastCallTime = 0;

    private volatile String lastError;
    private volatile String lastStatus = "Not started";
    /** Next requested side is tracked independently for every symbol. */
    private final java.util.Map<String, OrderSide> nextSideBySymbol = new ConcurrentHashMap<>();

    public AIAgent(String agentId, Account account, MatchingEngine engine,
                   List<String> symbols, String apiKey,
                   String apiEndpoint, long rateLimitMs) {
        super(agentId, account, engine);
        if (symbols == null || symbols.isEmpty()) throw new IllegalArgumentException("symbols cannot be empty");
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("apiKey cannot be empty");
        if (apiEndpoint == null || apiEndpoint.trim().isEmpty()) throw new IllegalArgumentException("apiEndpoint cannot be empty");

        this.symbols = List.copyOf(symbols);
        this.apiKey = apiKey;
        this.apiEndpoint = apiEndpoint;
        this.rateLimitMs = rateLimitMs;
    }

    @Override public String getType() { return "AI"; }
    @Override public void onMarketUpdate(com.trading.model.MarketData data) { /* scheduled decision loop drives API calls */ }
    public String getLastError() { return lastError; }
    public String getLastStatus() { return lastError == null ? lastStatus : "Error: " + lastError; }

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
                        : (bestBid != null ? bestBid : (bestAsk != null ? bestAsk : 100.0));

                // 3. 构建 prompt 并调用 LLM
                // ★ 改动 1：把行情传给 buildPrompt
                OrderSide preferredSide = preferredSide(symbol, lastPrice);
                String prompt = buildPrompt(symbol, bestBid, bestAsk, lastPrice, preferredSide);
                String jsonResponse = callLLM(prompt);

                // ★ 新增：打印 AI 原始返回，便于调试
                System.out.println("【AI 返回】: " + jsonResponse);

                // 4. 解析响应
                Order order = parseResponse(extractAssistantContent(jsonResponse), symbol);

                // ★ 改动 2：解析失败/LLM 返回 HOLD 时，用兜底随机下单
                if (order == null) {
                    lastError = null;
                    lastStatus = "" + symbol + ": model chose HOLD or returned no valid order";
                    System.out.println("[" + agentId + "] " + lastStatus);
                    continue;
                }

                // Enforce the demo's per-symbol alternation. The model still chooses
                // price and quantity, while the strategy controls BUY/SELL direction.
                int quantity = order.getQuantity();
                if (preferredSide == OrderSide.SELL) quantity = Math.min(quantity, account.getHolding(symbol));
                else quantity = Math.min(quantity, (int) (account.getCash() / order.getPrice()));
                if (quantity <= 0) {
                    lastStatus = symbol + ": cannot execute required " + preferredSide + " (insufficient position/cash)";
                    continue;
                }
                order = new Order(symbol, preferredSide, order.getPrice(), quantity, agentId);

                // 5. 安全校验
                if (!isPriceSafe(order.getPrice(), lastPrice)) {
                    lastStatus = symbol + ": order rejected by price safety check";
                    System.err.println("[" + agentId + "] " + symbol + " 价格波动过大，拒绝下单: " + order.getPrice());
                    continue;
                }

                // 校验数量
                if (order.getSide() == OrderSide.BUY && order.getPrice() * order.getQuantity() > account.getCash()) {
                    lastStatus = symbol + ": order rejected, insufficient cash";
                    System.err.println("[" + agentId + "] 现金不足，拒绝买单");
                    continue;
                }
                if (order.getSide() == OrderSide.SELL && order.getQuantity() > account.getHolding(symbol)) {
                    lastStatus = symbol + ": order rejected, insufficient holdings";
                    System.err.println("[" + agentId + "] 持仓不足，拒绝卖单");
                    continue;
                }

                // 6. 提交订单
                List<Trade> trades = engine.submitOrder(order);
                nextSideBySymbol.put(symbol, order.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY);
                lastError = null;
                lastStatus = symbol + ": " + order.getSide() + " " + order.getQuantity() + " @ " + order.getPrice();
                System.out.println("✅ [" + agentId + "] " + symbol + " 下单成功: "
                        + order.getSide() + " " + order.getQuantity() + " @ " + order.getPrice());

            } catch (Exception e) {
                System.err.println("❌ [" + agentId + "] " + symbol + " 决策出错: " + e.getMessage());
                lastError = e.getMessage();
                throw e;
            }
        }
    }

    // ★ 改动 1：buildPrompt 增加行情参数 + 明确要求交易
    private OrderSide preferredSide(String symbol, double lastPrice) {
        OrderSide preferred = nextSideBySymbol.getOrDefault(symbol, OrderSide.BUY);
        if (preferred == OrderSide.BUY && account.getCash() < lastPrice) {
            return account.getHolding(symbol) > 0 ? OrderSide.SELL : OrderSide.BUY;
        }
        if (preferred == OrderSide.SELL && account.getHolding(symbol) <= 0) return OrderSide.BUY;
        return preferred;
    }

    private String buildPrompt(String symbol, Double bestBid, Double bestAsk, double lastPrice, OrderSide preferredSide) {
        int holding = account.getHolding(symbol);
        double cash = account.getCash();

        return "你是一个积极参与模拟股票大厅的短线交易员。请为 " + symbol + " 立即提交一笔可执行的限价单。\n" +
                "【当前市场行情】\n" +
                "  参考价（买卖中间价）: " + String.format("%.2f", lastPrice) + "\n" +
                "  最优买价: " + (bestBid == null ? "无" : String.format("%.2f", bestBid)) + "\n" +
                "  最优卖价: " + (bestAsk == null ? "无" : String.format("%.2f", bestAsk)) + "\n" +
                "【你的账户】\n" +
                "  现金: " + String.format("%.2f", cash) + " 元\n" +
                "  持仓: " + holding + " 股\n" +
                "【激进演示策略】\n" +
                "1. 除非现金和持仓都不足，否则必须选 BUY 或 SELL，禁止 HOLD。\n" +
                "2. 本轮指定方向为 " + preferredSide + "，必须按该方向提交；不得自行改成另一方向。每只股票成功 BUY 后下一轮 SELL，成功 SELL 后下一轮 BUY。\n" +
                "3. 数量取 1 至 5 股，并确保总价不超过可用现金或卖出量不超过持仓。\n" +
                "4. 为尽量成交，BUY 价格取最优卖价（没有卖价则参考价上浮 0.3%）；SELL 价格取最优买价（没有买价则参考价下调 0.3%）。所有价格须在参考价 ±5% 内。\n" +
                "5. 只输出单行 JSON，不要 Markdown 或解释，格式严格为：\n" +
                "{\"action\":\"BUY\",\"price\":100.00,\"quantity\":2} 或 {\"action\":\"SELL\",\"price\":100.00,\"quantity\":2}。";
    }

    private String extractAssistantContent(String response) throws Exception {
        JsonNode root = JSON.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) throw new IllegalArgumentException("API response is missing choices[0].message.content");
        return content.asText();
    }

    // 调真实 LLM API
    private String callLLM(String prompt) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // ★ 改动 4：temperature 从 0.1 调高到 0.9，输出更激进
        String requestBody = "{\"model\": \"deepseek-chat\", \"messages\": [{\"role\": \"user\", \"content\": \""
                + prompt.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "")
                + "\"}], \"temperature\": 0.9}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(20))
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

    // 解析 JSON 响应
    private Order parseResponse(String json, String symbol) {
        try {
            Pattern actionPattern = Pattern.compile("\"action\"\\s*:\\s*\"(BUY|SELL|HOLD)\"");
            Matcher actionMatcher = actionPattern.matcher(json);
            if (!actionMatcher.find()) return null;
            String actionStr = actionMatcher.group(1);
            if ("HOLD".equals(actionStr)) return null;

            Pattern pricePattern = Pattern.compile("\"price\"\\s*:\\s*([0-9.]+)");
            Matcher priceMatcher = pricePattern.matcher(json);
            if (!priceMatcher.find()) return null;
            double price = Double.parseDouble(priceMatcher.group(1));

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
