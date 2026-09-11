package com.miao.login.listeners;

import com.miao.login.MiaoLogin;
import com.miao.login.managers.AuthManager;
import com.miao.login.utils.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * 聊天事件监听器 - 捕获玩家输入的密码
 *
 * 多语言: 所有提示通过 LangManager 自动按客户端语言选择
 * 安全: 密码错误超过 maxLoginFailures 次自动踢出
 */
public class PlayerChatListener implements Listener {

    private final MiaoLogin plugin;

    public PlayerChatListener(MiaoLogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();

        if (plugin.isAuthenticated(player)) {
            return;
        }

        AuthManager authManager = plugin.getAuthManager();
        AuthManager.PendingState pending = authManager.getPending(player);

        if (pending == null || pending.action == AuthManager.PendingAction.NONE) {
            event.setCancelled(true);
            plugin.sendMessage(player, "login-required-chat");
            plugin.sendMessage(player, "login-required-action");
            return;
        }

        event.setCancelled(true);
        // 去除首尾空格，避免 Bukkit 聊天事件携带多余空格导致密码校验失败
        final String message = event.getMessage().trim();

        // 切换到主线程处理密码输入
        SchedulerUtil.runForEntity(plugin, player, () -> handlePasswordInput(player, message));
    }

    /**
     * 处理密码输入 - 供 ModernChatListener (Paper AsyncChatEvent) 和本类共用
     */
    void handlePasswordInput(Player player, String input) {
        AuthManager authManager = plugin.getAuthManager();
        AuthManager.PendingState pending = authManager.getPending(player);
        if (pending == null) return;

        switch (pending.action) {
            case LOGIN_PASSWORD:
                handleLogin(player, input, pending);
                break;
            case REGISTER_PASSWORD:
                handleRegisterFirstInput(player, input, pending);
                break;
            case REGISTER_CONFIRM:
                handleRegisterConfirm(player, input, pending);
                break;
            case RESET_OLD_PASSWORD:
                handleResetOld(player, input, pending);
                break;
            case RESET_NEW_PASSWORD:
                handleResetNew(player, input, pending);
                break;
            case RESET_CONFIRM:
                handleResetConfirm(player, input, pending);
                break;
            default:
                break;
        }
    }

    // ========== 登录处理 ==========

    private void handleLogin(Player player, String password, AuthManager.PendingState pending) {
        if ("cancel".equalsIgnoreCase(password)) {
            plugin.sendMessage(player, "login-cancelled");
            plugin.getAuthManager().clearPending(player);
            plugin.getGuiManager().openLoginGUI(player);
            return;
        }

        plugin.sendMessage(player, "login-verifying");

        if (plugin.getDataManager().checkPassword(player.getName(), password)) {
            // 登录成功，清空密码错误计数
            plugin.resetLoginFail(player);
            plugin.getDataManager().updateLoginInfo(player.getName(),
                    player.getAddress() == null ? "" : player.getAddress().getAddress().getHostAddress());
            authSuccess(player);
        } else {
            // 密码错误，增加计数
            int fails = plugin.incrementLoginFail(player);
            int maxFails = plugin.getMaxLoginFailures();
            if (maxFails > 0 && fails >= maxFails) {
                // 达到上限，踢出
                String reason = plugin.getKickMessage(player, "kick-too-many-fails");
                player.kickPlayer(reason);
            } else {
                plugin.sendMessage(player, "login-failed");
                plugin.sendMessage(player, "login-prompt-retry");
            }
        }
    }

    // ========== 注册处理 ==========

    private void handleRegisterFirstInput(Player player, String password, AuthManager.PendingState pending) {
        if ("cancel".equalsIgnoreCase(password)) {
            plugin.sendMessage(player, "register-cancelled");
            plugin.getAuthManager().clearPending(player);
            plugin.getGuiManager().openLoginGUI(player);
            return;
        }

        if (!isValidPassword(player, password)) {
            plugin.sendMessage(player, "register-invalid",
                    plugin.getMinPasswordLength(), plugin.getMaxPasswordLength());
            plugin.sendMessage(player, "register-prompt");
            return;
        }

        pending.tempPassword = password;
        pending.action = AuthManager.PendingAction.REGISTER_CONFIRM;
        plugin.sendMessage(player, "register-confirm");
        plugin.sendMessage(player, "register-confirm-hint");
    }

    private void handleRegisterConfirm(Player player, String password, AuthManager.PendingState pending) {
        if ("cancel".equalsIgnoreCase(password)) {
            plugin.sendMessage(player, "register-cancelled");
            plugin.getAuthManager().clearPending(player);
            plugin.getGuiManager().openLoginGUI(player);
            return;
        }

        if (password.equals(pending.tempPassword)) {
            plugin.getDataManager().createAccount(player.getName(), password);
            pending.tempPassword = null;
            authSuccess(player);
            plugin.sendMessage(player, "register-success");
        } else {
            plugin.sendMessage(player, "register-mismatch");
            pending.tempPassword = null;
            pending.action = AuthManager.PendingAction.REGISTER_PASSWORD;
            plugin.sendMessage(player, "register-prompt");
        }
    }

    // ========== 重置密码处理 ==========

    private void handleResetOld(Player player, String oldPassword, AuthManager.PendingState pending) {
        if ("cancel".equalsIgnoreCase(oldPassword)) {
            plugin.sendMessage(player, "reset-cancelled");
            plugin.getAuthManager().clearPending(player);
            plugin.getGuiManager().openLoginGUI(player);
            return;
        }

        if (!plugin.getDataManager().checkPassword(player.getName(), oldPassword)) {
            // 旧密码也算一次错误，进入计数
            int fails = plugin.incrementLoginFail(player);
            int maxFails = plugin.getMaxLoginFailures();
            if (maxFails > 0 && fails >= maxFails) {
                String reason = plugin.getKickMessage(player, "kick-too-many-fails");
                player.kickPlayer(reason);
            } else {
                plugin.sendMessage(player, "reset-old-wrong");
                plugin.sendMessage(player, "reset-old-prompt");
            }
            return;
        }

        // 旧密码正确，重置错误计数 (重置流程中不计入)
        plugin.resetLoginFail(player);
        pending.oldPassword = oldPassword;
        pending.action = AuthManager.PendingAction.RESET_NEW_PASSWORD;
        plugin.sendMessage(player, "reset-old-ok");
        plugin.sendMessage(player, "reset-new-prompt");
    }

    private void handleResetNew(Player player, String newPassword, AuthManager.PendingState pending) {
        if ("cancel".equalsIgnoreCase(newPassword)) {
            plugin.sendMessage(player, "reset-cancelled");
            plugin.getAuthManager().clearPending(player);
            plugin.getGuiManager().openLoginGUI(player);
            return;
        }

        if (!isValidPassword(player, newPassword)) {
            plugin.sendMessage(player, "reset-new-invalid",
                    plugin.getMinPasswordLength(), plugin.getMaxPasswordLength());
            plugin.sendMessage(player, "reset-new-prompt");
            return;
        }

        if (newPassword.equals(pending.oldPassword)) {
            plugin.sendMessage(player, "reset-new-same");
            return;
        }

        pending.tempPassword = newPassword;
        pending.action = AuthManager.PendingAction.RESET_CONFIRM;
        plugin.sendMessage(player, "reset-confirm");
        plugin.sendMessage(player, "reset-confirm-hint");
    }

    private void handleResetConfirm(Player player, String password, AuthManager.PendingState pending) {
        if ("cancel".equalsIgnoreCase(password)) {
            plugin.sendMessage(player, "reset-cancelled");
            plugin.getAuthManager().clearPending(player);
            plugin.getGuiManager().openLoginGUI(player);
            return;
        }

        if (password.equals(pending.tempPassword)) {
            plugin.getDataManager().updatePassword(player.getName(), password);
            pending.tempPassword = null;
            pending.oldPassword = null;
            plugin.sendMessage(player, "reset-success");
            plugin.getAuthManager().clearPending(player);
            plugin.getAuthManager().setPending(player, AuthManager.PendingAction.LOGIN_PASSWORD);
            plugin.getGuiManager().openLoginGUI(player);
            plugin.sendMessage(player, "reset-relogin");
        } else {
            plugin.sendMessage(player, "reset-mismatch");
            pending.tempPassword = null;
            pending.action = AuthManager.PendingAction.RESET_NEW_PASSWORD;
            plugin.sendMessage(player, "reset-new-prompt");
        }
    }

    // ========== 工具方法 ==========

    private void authSuccess(Player player) {
        plugin.getAuthManager().setLoggedIn(player);
        plugin.getGuiManager().closeGUI(player);
        plugin.getGuiManager().restoreFlightSafety(player);
        plugin.sendMessage(player, "login-success");
        try {
            player.sendTitle(
                    plugin.getLangManager().getTitle(player, "login-success-title"),
                    plugin.getLangManager().getTitle(player, "login-success-subtitle"),
                    5, 40, 10);
        } catch (NoSuchMethodError ignored) {
        }
    }

    private boolean isValidPassword(Player player, String password) {
        if (password == null) return false;
        // 去除首尾空格，避免玩家不小心输入空格导致长度判断错误
        String trimmed = password.trim();
        int len = trimmed.length();
        int min = plugin.getMinPasswordLength();
        int max = plugin.getMaxPasswordLength();
        if (len < min || len > max) {
            plugin.getLogger().fine("密码长度校验失败，玩家: " + player.getName()
                    + "，输入长度(去空格): " + len + "，允许范围: " + min + "-" + max);
            return false;
        }
        return true;
    }
}