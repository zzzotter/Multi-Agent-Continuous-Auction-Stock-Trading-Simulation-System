package com.trading.gui;

import com.trading.account.AccountManager;
import com.trading.agent.AIAgent; // 新增导入
import com.trading.engine.MatchingEngine;
import com.trading.engine.OrderBook;
import com.trading.model.Account;
import com.trading.model.Order;
import com.trading.model.OrderSide;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors; // 新增导入
import java.util.concurrent.ScheduledExecutorService; // 新增导入
import java.util.concurrent.TimeUnit; // 新增导入

import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.property.SimpleStringProperty;

import com.trading.model.Trade;

/**
 * 主界面控制器
 */
public class MainViewController {

    @FXML private ComboBox<String> symbolBox;
    @FXML private ComboBox<OrderSide> sideBox;
    @FXML private TextField priceField;
    @FXML private TextField qtyField;
    @FXML private Button submitBtn;
    @FXML private Label statusLabel;

    @FXML private ComboBox<String> bookSymbolBox;
    @FXML private ListView<String> bidsList;
    @FXML private ListView<String> asksList;

    @FXML private Label agentLabel;
    @FXML private Label cashLabel;
    @FXML private ListView<String> holdingsList;
    @FXML private Label equityLabel;

    @FXML private Label bottomStatus;

    // ===== 新增：AI 相关的控件 =====
    @FXML private Button startAiBtn;
    @FXML private Button stopAiBtn;
    private AIAgent aiAgent;
    private ScheduledExecutorService aiScheduler;
    // ==============================

    // 价格图
    @FXML private LineChart<String, Number> priceChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    // 交易历史
    @FXML private TableView<TradeRow> tradeTable;
    @FXML private TableColumn<TradeRow, String> colTime;
    @FXML private TableColumn<TradeRow, String> colSymbol;
    @FXML private TableColumn<TradeRow, String> colPrice;
    @FXML private TableColumn<TradeRow, String> colQty;
    @FXML private TableColumn<TradeRow, String> colBuyer;
    @FXML private TableColumn<TradeRow, String> colSeller;

    // 价格数据：每只股票一条曲线
    private final Map<String, XYChart.Series<String, Number>> priceSeries = new java.util.HashMap<>();
    private int timeCounter = 0;

    private MatchingEngine engine;
    private AccountManager accountManager;
    private String currentAgentId;

    private final ObservableList<String> bidsData = FXCollections.observableArrayList();
    private final ObservableList<String> asksData = FXCollections.observableArrayList();
    private final ObservableList<String> holdingsData = FXCollections.observableArrayList();

    /** 定时刷新器 */
    private Timeline refreshTimer;

    public void init(MatchingEngine engine, AccountManager accountManager, String agentId) {
        this.engine = engine;
        this.accountManager = accountManager;
        this.currentAgentId = agentId;

        // 初始化下拉框
        List<String> symbols = List.of("AAPL", "GOOGL", "TSLA");
        symbolBox.setItems(FXCollections.observableArrayList(symbols));
        symbolBox.getSelectionModel().selectFirst();

        bookSymbolBox.setItems(FXCollections.observableArrayList(symbols));
        bookSymbolBox.getSelectionModel().selectFirst();

        sideBox.setItems(FXCollections.observableArrayList(OrderSide.values()));
        sideBox.getSelectionModel().selectFirst();

        // 设置 ListView 数据源
        bidsList.setItems(bidsData);
        asksList.setItems(asksData);
        holdingsList.setItems(holdingsData);

        // 绑定事件
        submitBtn.setOnAction(e -> handleSubmit());
        bookSymbolBox.setOnAction(e -> refreshOrderBook());

        // 启动定时刷新（每 500ms）
        startRefreshTimer();

        // 首次刷新
        refreshAll();

        bottomStatus.setText("Ready. Agent: " + currentAgentId);

        // 初始化价格图
        for (String symbol : symbols) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(symbol);
            priceChart.getData().add(series);
            priceSeries.put(symbol, series);
        }

        // 初始化交易历史表
        colTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().time));
        colSymbol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().symbol));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().price));
        colQty.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().qty));
        colBuyer.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().buyer));
        colSeller.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().seller));

        // ==================== AIAgent 接入部分 ====================
        
        // 1. 初始化 AIAgent 
        // ⚠️ 组长测的时候，把 "sk-test_key_placeholder" 换成真实的 Key，测完记得改回来，别提交到 GitHub！
        Account aiAcc = accountManager.getAccount("ai_01");
        if (aiAcc != null) {
            aiAgent = new AIAgent(
                    "ai_01",
                    aiAcc,
                    engine,
                    symbols,
                    "sk-test_key_placeholder", // 测试时换成真实 Key
                    "https://api.deepseek.com/chat/completions",
                    3000 // 限流 3 秒
            );
        }

        // 2. 绑定 Start 按钮
        startAiBtn.setOnAction(e -> {
            if (aiScheduler != null && !aiScheduler.isShutdown()) {
                bottomStatus.setText("AI 已经在运行中！");
                return;
            }
            bottomStatus.setText("🚀 AI 自动交易已启动，每 3 秒决策一次...");
            
            // 创建单线程调度器（必须在后台线程执行，不能阻塞 GUI 线程）
            aiScheduler = Executors.newSingleThreadScheduledExecutor();
            aiScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (aiAgent != null) {
                        aiAgent.decide(); // 核心方法
                    }
                } catch (Exception ex) {
                    System.err.println("AI 决策出错: " + ex.getMessage());
                    Platform.runLater(() -> bottomStatus.setText("❌ AI 出错: " + ex.getMessage()));
                }
            }, 0, 3, TimeUnit.SECONDS); // 首次0秒执行，之后每3秒执行一次
        });

        // 3. 绑定 Stop 按钮
        stopAiBtn.setOnAction(e -> {
            if (aiScheduler != null && !aiScheduler.isShutdown()) {
                aiScheduler.shutdown(); // 停止定时器
                aiScheduler = null;
                bottomStatus.setText("🛑 AI 自动交易已停止。");
            } else {
                bottomStatus.setText("AI 本来就没在运行。");
            }
        });
        // =========================================================
    }

    private void handleSubmit() {
        try {
            String symbol = symbolBox.getValue();
            OrderSide side = sideBox.getValue();
            double price = Double.parseDouble(priceField.getText().trim());
            int qty = Integer.parseInt(qtyField.getText().trim());

            if (price <= 0 || qty <= 0) {
                showStatus("Price and quantity must be positive", true);
                return;
            }

            Order order = new Order(symbol, side, price, qty, currentAgentId);
            engine.submitOrder(order);

            showStatus("Submitted: " + order, false);
            priceField.clear();
            qtyField.clear();

            refreshAll();
        } catch (NumberFormatException ex) {
            showStatus("Invalid number format", true);
        } catch (IllegalArgumentException ex) {
            showStatus(ex.getMessage(), true);
        } catch (Exception ex) {
            showStatus("Error: " + ex.getMessage(), true);
        }
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
    }

    private void startRefreshTimer() {
        refreshTimer = new Timeline(
                new KeyFrame(Duration.millis(500), e -> refreshAll())
        );
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private void refreshAll() {
        refreshOrderBook();
        refreshAccount();
        refreshPriceChart();
        refreshTradeHistory();
    }

    private void refreshPriceChart() {
        Platform.runLater(() -> {
            timeCounter++;
            String timeLabel = String.valueOf(timeCounter);
            for (String symbol : priceSeries.keySet()) {
                Double price = engine.getLastPrice(symbol);
                if (price != null) {
                    XYChart.Series<String, Number> series = priceSeries.get(symbol);
                    series.getData().add(new XYChart.Data<>(timeLabel, price));
                    if (series.getData().size() > 30) {
                        series.getData().remove(0);
                    }
                }
            }
        });
    }

    private void refreshTradeHistory() {
        Platform.runLater(() -> {
            List<Trade> trades = engine.getAllTrades();
            ObservableList<TradeRow> rows = FXCollections.observableArrayList();
            for (int i = trades.size() - 1; i >= Math.max(0, trades.size() - 50); i--) {
                Trade t = trades.get(i);
                rows.add(new TradeRow(
                        t.getTimestamp().toLocalTime().withNano(0).toString(),
                        t.getSymbol(),
                        String.format("%.2f", t.getPrice()),
                        String.valueOf(t.getQuantity()),
                        t.getBuyerAgentId(),
                        t.getSellerAgentId()
                ));
            }
            tradeTable.setItems(rows);
        });
    }

    private void refreshOrderBook() {
        String symbol = bookSymbolBox.getValue();
        if (symbol == null) return;

        OrderBook book = engine.getOrderBook(symbol);
        if (book == null) return;

        Platform.runLater(() -> {
            bidsData.clear();
            for (Order o : book.getTopBids(5)) {
                bidsData.add(String.format("%.2f x %d", o.getPrice(), o.getRemainingQuantity()));
            }
            asksData.clear();
            for (Order o : book.getTopAsks(5)) {
                asksData.add(String.format("%.2f x %d", o.getPrice(), o.getRemainingQuantity()));
            }
        });
    }

    private void refreshAccount() {
        Account acc = accountManager.getAccount(currentAgentId);
        if (acc == null) return;

        Platform.runLater(() -> {
            agentLabel.setText("Agent: " + acc.getAgentId());
            cashLabel.setText(String.format("Cash: $%.2f", acc.getCash()));

            holdingsData.clear();
            for (Map.Entry<String, Integer> e : acc.getHoldings().entrySet()) {
                holdingsData.add(e.getKey() + ": " + e.getValue());
            }

            equityLabel.setText(String.format("Total Equity: $%.2f", acc.getCash()));
        });
    }

    public static class TradeRow {
        public final String time, symbol, price, qty, buyer, seller;
        public TradeRow(String time, String symbol, String price,
                        String qty, String buyer, String seller) {
            this.time = time;
            this.symbol = symbol;
            this.price = price;
            this.qty = qty;
            this.buyer = buyer;
            this.seller = seller;
        }
    }
}
