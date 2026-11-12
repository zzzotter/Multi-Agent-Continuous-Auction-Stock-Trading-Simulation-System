package com.trading.gui;

import com.trading.account.AccountManager;
import com.trading.engine.MatchingEngine;
import com.trading.model.Account;
import com.trading.model.ParticipantType;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;

/**
 * JavaFX 应用入口
 */
public class TradingApp extends Application {

    private static final List<String> SYMBOLS = List.of("AAPL", "GOOGL", "TSLA");

    @Override
    public void start(Stage stage) throws Exception {
        // 1. 初始化核心系统
        AccountManager accountManager = new AccountManager();
        MatchingEngine engine = new MatchingEngine(accountManager);

        for (String symbol : SYMBOLS) {
            engine.registerSymbol(symbol);
        }

        // 2. 创建人类参与者（默认每户 $100,000，并持有每种股票 100 股）
        for (int i = 1; i <= 2; i++) {
            String id = "human" + i;
            java.util.Map<String, Integer> holdings = new java.util.HashMap<>();
            for (String s : SYMBOLS) holdings.put(s, 100);
            accountManager.openAccount(id, "Human " + i, ParticipantType.HUMAN,
                    100_000.0, holdings, engine, SYMBOLS);
        }

        // ===== 新增：创建 AI 交易员账户 =====
        // AI 账户仅在设置 TRADING_AI_API_KEY 后自动创建；API 故障不会触发随机下单。
        if (System.getenv("TRADING_AI_API_KEY") != null && !System.getenv("TRADING_AI_API_KEY").isBlank()) {
            java.util.Map<String, Integer> holdings = new java.util.HashMap<>();
            for (String s : SYMBOLS) holdings.put(s, 100);
            accountManager.openAccount("ai_01", "AI 1", ParticipantType.AI,
                    100_000.0, holdings, engine, SYMBOLS);
        }
        // ==================================

        // 3. 加载 FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main-view.fxml"));
        Parent root = loader.load();

        // 4. 把核心系统传给控制器
        MainViewController controller = loader.getController();
        controller.init(engine, accountManager, "human1");

        // 5. 显示窗口
        Scene scene = new Scene(root, 1100, 700);
        stage.setTitle("Multi-Agent Trading System");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
