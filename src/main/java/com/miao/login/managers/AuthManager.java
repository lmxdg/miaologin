package com.miao.login.managers;

import com.miao.login.MiaoLogin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 认证管理器 - 管理玩家登录状态和待处理输入
 */
public class AuthManager {

    public enum PendingAction {
        NONE,
        LOGIN_PASSWORD,
        REGISTER_PASSWORD,
        REGISTER_CONFIRM,
        RESET_OLD_PASSWORD,
        RESET_NEW_PASSWORD,
        RESET_CONFIRM
    }

    public static class PendingState {
        public PendingAction action;
        public String tempPassword;
        public String oldPassword;

        public PendingState(PendingAction action) {
            this.action = action;
        }
    }

    private final MiaoLogin plugin;
    private final DataManager dataManager;
    private final Set<UUID> loggedInPlayers = new HashSet<>();
    private final Map<UUID, PendingState> pendingStates = new HashMap<>();

    public AuthManager(MiaoLogin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public boolean isLoggedIn(Player player) {
        return isLoggedIn(player.getUniqueId());
    }

    public boolean isLoggedIn(UUID uuid) {
        return loggedInPlayers.contains(uuid);
    }

    public void setLoggedIn(Player player) {
        loggedInPlayers.add(player.getUniqueId());
        clearPending(player);
    }

    public void logout(Player player) {
        loggedInPlayers.remove(player.getUniqueId());
        pendingStates.remove(player.getUniqueId());
    }

    public PendingState getPending(Player player) {
        return pendingStates.get(player.getUniqueId());
    }

    public void setPending(Player player, PendingAction action) {
        pendingStates.put(player.getUniqueId(), new PendingState(action));
    }

    public void clearPending(Player player) {
        pendingStates.remove(player.getUniqueId());
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}