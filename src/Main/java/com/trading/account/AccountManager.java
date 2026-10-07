package com.trading.account;

import com.trading.model.Account;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccountManager {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

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
