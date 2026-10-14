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
import java.util.Random;                     // ★ 新增
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
    private long lastCallTime = 0;

    // ★ 新增：兜底随机数发生器
    private final Random random = new Random();

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
                        : (bestBid != null ? bestBid : (bestAsk != null ? bestAsk : 100.0));

                // 3. 构建 prompt 并调用 LLM
                // ★ 改动 1：把行情传给 buildPrompt
                String prompt = buildPrompt(symbol, bestBid, bestAsk, lastPrice);
                String jsonResponse = callLLM(prompt);

                // ★ 新增：打印 AI 原始返回，便于调试
                System.out.println("【AI 返回】: " + jsonResponse);

                // 4. 解析响应
                Order order = parseResponse(jsonResponse, symbol);

                // ★ 改动 2：解析失败/LLM 返回 HOLD 时，用兜底随机下单
                if (order == null) {
                    order = fallbackOrder(symbol, lastPrice);
                    if (order == null) continue;
                    System.out.println("【兜底下单】: " + order.getSide() + " "
                            + order.getQuantity() + " @ " + order.getPrice());
                }

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
                System.out.println("✅ [" + agentId + "] " + symbol + " 下单成功: "
                        + order.getSide() + " " + order.getQuantity() + " @ " + order.getPrice());

            } catch (Exception e) {
                System.err.println("❌ [" + agentId + "] " + symbol + " 决策出错: " + e.getMessage());
                // ★ 改动 3：即使 API 挂了，也用兜底下单，保证演示不空场
                try {
                    Order fallback = fallbackOrder(symbol, 100.0);
                    if (fallback != null) {
                        engine.submitOrder(fallback);
                        System.out.println("【兜底下单(异常后)】: " + fallback.getSide()
                                + " " + fallback.getQuantity() + " @ " + fallback.getPrice());
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ★ 改动 1：buildPrompt 增加行情参数 + 明确要求交易
    private String buildPrompt(String symbol, Double bestBid, Double bestAsk, double lastPrice) {
        int holding = account.getHolding(symbol);
        double cash = account.getCash();

        return "你是一个量化交易 AI，请对股票 " + symbol + " 做出交易决策。\n" +
                "【当前市场行情】\n" +
                "  参考价（买卖中间价）: " + String.format("%.2f", lastPrice) + "\n" +
                "  最优买价: " + (bestBid == null ? "无" : String.format("%.2f", bestBid)) + "\n" +
                "  最优卖价: " + (bestAsk == null ? "无" : String.format("%.2f", bestAsk)) + "\n" +
                "【你的账户】\n" +
                "  现金: " + String.format("%.2f", cash) + " 元\n" +
                "  持仓: " + holding + " 股\n" +
                "【要求】\n" +
                "  1. 出价必须在参考价的 ±5% 以内\n" +
                "  2. 你必须从 BUY / SELL / HOLD 中选一个\n" +
                "  3. 当前是模拟交易，请尽量做出 BUY 或 SELL 决策，不要一直 HOLD\n" +
                "  4. 严格返回 JSON，不要任何其他文字：\n" +
                "     {\"action\": \"BUY\" | \"SELL\" | \"HOLD\", \"price\": 数值, \"quantity\": 整数}";
    }

    // 调真实 LLM API
    private String callLLM(String prompt) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
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

    // ★ 新增：兜底策略——LLM 说 HOLD 或失败时，随机下一单
    private Order fallbackOrder(String symbol, double lastPrice) {
        int holding = account.getHolding(symbol);
        double cash = account.getCash();

        boolean canBuy  = cash >= lastPrice * 10;
        boolean canSell = holding >= 10;
        if (!canBuy && !canSell) return null;

        OrderSide side;
        if (canBuy && canSell) {
            side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        } else if (canBuy) {
            side = OrderSide.BUY;
        } else {
            side = OrderSide.SELL;
        }

        // 价格在参考价 ±2% 内随机
        double factor = 0.98 + random.nextDouble() * 0.04;
        double price = Math.round(lastPrice * factor * 100.0) / 100.0;
        int qty = 10 + random.nextInt(40);

        // 简单校验
        if (side == OrderSide.BUY && price * qty > cash) return null;
        if (side == OrderSide.SELL && qty > holding) return null;

        return new Order(symbol, side, price, qty, agentId);
    }

    // 安全校验：价格在 lastPrice 的 ±5% 内
    private boolean isPriceSafe(double price, double lastPrice) {
        if (lastPrice <= 0) return true;
        double changePercent = Math.abs(price - lastPrice) / lastPrice;
        return changePercent <= 0.05;
    }
}