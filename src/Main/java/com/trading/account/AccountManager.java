package com.trading.account;

import com.trading.model.Account;
import com.trading.model.ParticipantProfile;
import com.trading.model.ParticipantType;
import com.trading.agent.Agent;
import com.trading.agent.HumanAgent;
import com.trading.agent.AIAgent;
import com.trading.engine.MatchingEngine;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AccountManager {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, ParticipantProfile> participants = new ConcurrentHashMap<>();
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /** Creates and registers a participant. AI keys are read from TRADING_AI_API_KEY. */
    public synchronized ParticipantProfile openAccount(String agentId, String name,
            ParticipantType type, double initialCash, Map<String, Integer> initialHoldings,
            MatchingEngine engine, List<String> symbols) {
        if (agentId == null || agentId.trim().isEmpty()) throw new IllegalArgumentException("agentId cannot be blank");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("name cannot be blank");
        if (type == null) throw new IllegalArgumentException("participant type is required");
        if (!Double.isFinite(initialCash) || initialCash < 0) throw new IllegalArgumentException("initialCash must be non-negative");
        if (containsAccount(agentId)) throw new IllegalArgumentException("Account already exists for agentId: " + agentId);
        Map<String, Integer> holdings = initialHoldings == null ? Map.of() : initialHoldings;
        for (Map.Entry<String, Integer> e : holdings.entrySet()) {
            if (e.getKey() == null || e.getKey().trim().isEmpty() || e.getValue() == null || e.getValue() < 0)
                throw new IllegalArgumentException("holding symbols must be nonblank and quantities non-negative");
        }
        if (engine == null) throw new IllegalArgumentException("engine cannot be null");
        Account account = new Account(agentId.trim(), initialCash);
        for (Map.Entry<String, Integer> e : holdings.entrySet()) if (e.getValue() > 0) account.addHolding(e.getKey(), e.getValue());
        Agent agent;
        if (type == ParticipantType.HUMAN) agent = new HumanAgent(agentId.trim(), account, engine);
        else {
            Properties localConfig = new Properties();
            Path configPath = Path.of("ai-local.properties");
            if (Files.isRegularFile(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) { localConfig.load(in); }
                catch (Exception e) { throw new IllegalStateException("Cannot read ai-local.properties", e); }
            }
            String key = localConfig.getProperty("api.key", System.getenv("TRADING_AI_API_KEY"));
            if (key == null || key.trim().isEmpty()) throw new IllegalStateException("Set api.key in ai-local.properties or TRADING_AI_API_KEY");
            String endpoint = localConfig.getProperty("api.endpoint",
                    System.getenv().getOrDefault("TRADING_AI_API_ENDPOINT", "https://api.deepseek.com/chat/completions"));
            agent = new AIAgent(agentId.trim(), account, engine, symbols, key,
                    endpoint, 800);
        }
        addAccount(account);
        agents.put(agentId.trim(), agent);
        ParticipantProfile profile = new ParticipantProfile(agentId.trim(), name.trim(), type);
        participants.put(agentId.trim(), profile);
        return profile;
    }

    public Agent getAgent(String agentId) { return agents.get(agentId); }
    public Map<String, Agent> getAllAgents() { return new ConcurrentHashMap<>(agents); }
    public ParticipantProfile getParticipant(String agentId) { return participants.get(agentId); }
    public Map<String, ParticipantProfile> getAllParticipants() { return new ConcurrentHashMap<>(participants); }

    public Account getAccount(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new IllegalArgumentException("agentId cannot be null or empty");
        }
        return accounts.get(agentId);
    }

    public void addAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("account cannot be null");
        }
        String agentId = account.getAgentId();
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new IllegalArgumentException("account agentId cannot be null or empty");
        }
        Account existing = accounts.putIfAbsent(agentId, account);
        if (existing != null) {
            throw new IllegalArgumentException("Account already exists for agentId: " + agentId);
        }
    }

    public Map<String, Account> getAllAccounts() {
        return new ConcurrentHashMap<>(accounts); // 返回副本
    }

    public int size() {
        return accounts.size();
    }

    public boolean containsAccount(String agentId) {
        return accounts.containsKey(agentId);
    }

    public void removeAccount(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new IllegalArgumentException("agentId cannot be null or empty");
        }
        accounts.remove(agentId);
    }
}
