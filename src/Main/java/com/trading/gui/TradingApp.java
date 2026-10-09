package com.trading.gui;

import com.trading.account.AccountManager;
import com.trading.engine.MatchingEngine;
import com.trading.model.Account;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;

/**
 * JavaFX 应用入口
 *
 * 职责：
 * 1. 初始化核心系统（AccountManager、MatchingEngine）
 * 2. 加载主界面
 * 3. 把核心系统注入到控制器
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

        // 2. 创建一些 Agent 账户
        for (int i = 1; i <= 2; i++) {
            String id = "human" + i;
            Account acc = new Account(id, 100_000.0);
            for (String s : SYMBOLS) {
                acc.addHolding(s, 100);
            }
            accountManager.addAccount(acc);
        }

        // 3. 加载 FXML
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/main-view.fxml"));
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
