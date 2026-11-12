package com.trading.gui;

import com.trading.account.AccountManager;
import com.trading.agent.AIAgent;
import com.trading.agent.Agent;
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
import com.trading.model.ParticipantType;
import com.trading.model.ParticipantProfile;

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
    @FXML private ComboBox<String> accountBox;
    @FXML private ListView<String> participantsList;
    @FXML private TextField newIdField, newNameField, newCashField, newHoldingsField;
    @FXML private ComboBox<ParticipantType> newTypeBox;
    @FXML private Button openAccountBtn;

    // ===== 新增：AI 相关的控件 =====
    @FXML private Button startAiBtn;
    @FXML private Button stopAiBtn;
    @FXML private Button startAllAiBtn;
    @FXML private Button stopAllAiBtn;
    @FXML private ComboBox<String> aiAgentBox;
    private final Map<String, ScheduledExecutorService> aiSchedulers = new java.util.concurrent.ConcurrentHashMap<>();
    // ==============================

    // 价格图
    @FXML private LineChart<String, Number> priceChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    // 交易历史
    @FXML private TableView<TradeRow> tradeTable;
    @FXML private ComboBox<String> tradeSymbolBox;
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
    private static final int MARKET_REFRESH_MS = 1000;
    private static final int ACCOUNT_REFRESH_MS = 1500;
    private static final int CHART_REFRESH_MS = 2000;
    private static final int TRADE_REFRESH_MS = 500;
    private static final int MAX_TRADE_ROWS = 100;
    private long lastAccountRefresh, lastChartRefresh;
    private int displayedTradeCount;
    private final ObservableList<TradeRow> tradeRows = FXCollections.observableArrayList();
    private final List<TradeRow> recentTradeRows = new java.util.ArrayList<>();
    private final List<String> symbols = List.of("AAPL", "GOOGL", "TSLA");

    public void init(MatchingEngine engine, AccountManager accountManager, String agentId) {
        this.engine = engine;
        this.accountManager = accountManager;
        this.currentAgentId = agentId;

        // 初始化下拉框
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
        tradeTable.setItems(tradeRows);
        tradeSymbolBox.setItems(FXCollections.observableArrayList("ALL", "AAPL", "GOOGL", "TSLA"));
        tradeSymbolBox.setValue("ALL");
        tradeSymbolBox.setOnAction(e -> applyTradeFilter());
        priceChart.setAnimated(false);
        accountBox.setItems(FXCollections.observableArrayList(accountManager.getAllAccounts().keySet()));
        accountBox.setValue(agentId);
        accountBox.setOnAction(e -> {
            String selected = accountBox.getValue();
            if (selected != null && accountManager.containsAccount(selected)) {
                currentAgentId = selected;
                refreshAccount();
            }
        });
        newTypeBox.setItems(FXCollections.observableArrayList(ParticipantType.values()));
        newTypeBox.setValue(ParticipantType.HUMAN);
        openAccountBtn.setOnAction(e -> createParticipant());
        refreshParticipants();

        // 绑定事件
        submitBtn.setOnAction(e -> handleSubmit());
        bookSymbolBox.setOnAction(e -> refreshOrderBook());

        // 启动定时刷新（每 500ms）
        startRefreshTimer();

        // 首次刷新
        refreshAll();
        javafx.application.Platform.runLater(() -> {
            if (priceChart.getScene() != null) priceChart.getScene().getWindow().setOnHidden(e -> shutdown());
        });

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
        
        refreshAiChoices();
        startAiBtn.setOnAction(e -> startSelectedAi());
        stopAiBtn.setOnAction(e -> stopSelectedAi());
        startAllAiBtn.setOnAction(e -> accountManager.getAllAgents().forEach((id, a) -> { if (a instanceof AIAgent) startAi(id, (AIAgent) a); }));
        stopAllAiBtn.setOnAction(e -> new java.util.ArrayList<>(aiSchedulers.keySet()).forEach(this::stopAi));
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

    private void createParticipant() {
        try {
            String id = newIdField.getText().trim();
            String name = newNameField.getText().trim();
            double cash = newCashField.getText().isBlank() ? 100_000.0 : Double.parseDouble(newCashField.getText().trim());
            Map<String, Integer> holdings = new java.util.HashMap<>();
            if (!newHoldingsField.getText().isBlank()) for (String item : newHoldingsField.getText().split(",")) {
                String[] pair = item.trim().split(":");
                if (pair.length != 2) throw new IllegalArgumentException("Holdings format: SYMBOL:quantity,SYMBOL:quantity");
                holdings.put(pair[0].trim().toUpperCase(), Integer.parseInt(pair[1].trim()));
            }
            accountManager.openAccount(id, name, newTypeBox.getValue(), cash, holdings, engine, symbols);
            refreshParticipants();
            accountBox.setValue(id);
            currentAgentId = id;
            refreshAiChoices();
            showStatus("Account created: " + id, false);
        } catch (Exception ex) { showStatus("Cannot create account: " + ex.getMessage(), true); }
    }

    private void refreshParticipants() {
        List<String> rows = new java.util.ArrayList<>();
        accountManager.getAllParticipants().values().stream()
                .sorted(java.util.Comparator.comparing(ParticipantProfile::getAgentId))
                .forEach(p -> rows.add(p.getName() + " (" + p.getAgentId() + ", " + p.getType() + ")"));
        participantsList.getItems().setAll(rows);
        String selected = currentAgentId;
        List<String> accountIds = accountManager.getAllAccounts().keySet().stream().sorted().toList();
        if (!accountIds.equals(accountBox.getItems())) {
            accountBox.setOnAction(null);
            accountBox.getItems().setAll(accountIds);
            String restore = selected != null && accountIds.contains(selected)
                    ? selected : (accountIds.isEmpty() ? null : accountIds.get(0));
            accountBox.setValue(restore);
            currentAgentId = restore;
            accountBox.setOnAction(e -> {
                String id = accountBox.getValue();
                if (id != null && accountManager.containsAccount(id)) {
                    currentAgentId = id;
                    refreshAccount();
                }
            });
        }
    }

    private void shutdown() {
        if (refreshTimer != null) refreshTimer.stop();
        new java.util.ArrayList<>(aiSchedulers.keySet()).forEach(this::stopAi);
    }

    private void refreshAiChoices() {
        if (accountManager == null || aiAgentBox == null) return;
        String selected = aiAgentBox.getValue();
        List<String> ids = new java.util.ArrayList<>();
        accountManager.getAllAgents().forEach((id, agent) -> { if (agent instanceof AIAgent) ids.add(id); });
        java.util.Collections.sort(ids);
        aiAgentBox.setItems(FXCollections.observableArrayList(ids));
        if (selected != null && ids.contains(selected)) aiAgentBox.setValue(selected);
        else if (!ids.isEmpty()) aiAgentBox.setValue(ids.get(0));
    }

    private void startSelectedAi() {
        String id = aiAgentBox.getValue();
        Agent agent = id == null ? null : accountManager.getAgent(id);
        if (agent instanceof AIAgent) startAi(id, (AIAgent) agent);
        else bottomStatus.setText("No AI participant selected.");
    }

    private void startAi(String id, AIAgent agent) {
        if (aiSchedulers.containsKey(id)) { bottomStatus.setText(id + " is already running."); return; }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-agent-" + id); t.setDaemon(true); return t;
        });
        ScheduledExecutorService previous = aiSchedulers.putIfAbsent(id, scheduler);
        if (previous != null) { scheduler.shutdownNow(); return; }
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                agent.decide();
                Platform.runLater(() -> bottomStatus.setText(id + ": " + agent.getLastStatus()));
            }
            catch (Exception ex) {
                System.err.println("AI " + id + " decision failed: " + ex.getMessage());
                Platform.runLater(() -> bottomStatus.setText(id + " error: " + ex.getMessage()));
            }
        }, 0, 3, TimeUnit.SECONDS);
        refreshAiChoices();
        bottomStatus.setText("Started AI participant " + id + ".");
    }

    private void stopSelectedAi() {
        String id = aiAgentBox.getValue();
        if (id != null) stopAi(id);
    }

    private void stopAi(String id) {
        ScheduledExecutorService scheduler = aiSchedulers.remove(id);
        if (scheduler != null) { scheduler.shutdownNow(); bottomStatus.setText("Stopped AI participant " + id + "."); }
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
    }

    private void startRefreshTimer() {
        refreshTimer = new Timeline(
                new KeyFrame(Duration.millis(MARKET_REFRESH_MS), e -> refreshAll())
        );
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private void refreshAll() {
        refreshOrderBook();
        long now = System.currentTimeMillis();
        if (now - lastAccountRefresh >= ACCOUNT_REFRESH_MS) { refreshAccount(); refreshParticipants(); lastAccountRefresh = now; }
        if (now - lastChartRefresh >= CHART_REFRESH_MS) { refreshPriceChart(); lastChartRefresh = now; }
        refreshTradeHistory();
    }

    private void refreshPriceChart() {
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
    }

    private void refreshTradeHistory() {
            List<Trade> trades = engine.getAllTrades();
            if (trades.size() == displayedTradeCount) return;
            for (int i = displayedTradeCount; i < trades.size(); i++) {
                Trade t = trades.get(i);
                tradeRows.add(0, new TradeRow(
                        t.getTimestamp().toLocalTime().withNano(0).toString(),
                        t.getSymbol(),
                        String.format("%.2f", t.getPrice()),
                        String.valueOf(t.getQuantity()),
                        t.getBuyerAgentId(),
                        t.getSellerAgentId()
                ));
                recentTradeRows.add(0, tradeRows.get(0));
            }
            displayedTradeCount = trades.size();
            while (recentTradeRows.size() > MAX_TRADE_ROWS) recentTradeRows.remove(recentTradeRows.size() - 1);
            applyTradeFilter();
    }

    private void applyTradeFilter() {
        if (tradeSymbolBox == null) return;
        String selected = tradeSymbolBox.getValue();
        tradeRows.setAll(recentTradeRows.stream()
                .filter(row -> selected == null || "ALL".equals(selected) || selected.equals(row.symbol))
                .toList());
    }

    private void refreshOrderBook() {
        String symbol = bookSymbolBox.getValue();
        if (symbol == null) return;

        OrderBook book = engine.getOrderBook(symbol);
        if (book == null) return;

            bidsData.clear();
            for (Order o : book.getTopBids(5)) {
                bidsData.add(String.format("%.2f x %d", o.getPrice(), o.getRemainingQuantity()));
            }
            asksData.clear();
            for (Order o : book.getTopAsks(5)) {
                asksData.add(String.format("%.2f x %d", o.getPrice(), o.getRemainingQuantity()));
            }
    }

    private void refreshAccount() {
        String id = currentAgentId;
        if (id == null || id.isBlank() || !accountManager.containsAccount(id)) return;
        Account acc = accountManager.getAccount(id);
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
