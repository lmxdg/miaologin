package com.miao.login.listeners;

import com.miao.login.MiaoLogin;
import com.miao.login.utils.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家进/退服监听器
 *
 * - 进服: 清除密码错误计数，打开 GUI，启动登录超时计时器
 * - 退服: 清理认证状态和密码错误计数
 *
 * 所有延迟任务通过 SchedulerUtil 调度 (在主线程执行)
 */
public class PlayerJoinListener implements Listener {

    private final MiaoLogin plugin;

    public PlayerJoinListener(MiaoLogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        plugin.getAuthManager().clearPending(player);
        plugin.resetLoginFail(player); // 进服时清空密码错误计数

        boolean registered = plugin.getDataManager().isRegistered(player.getName());
        boolean authMeMode = plugin.isAuthMeTakeoverEnabled();
        boolean isLocalAccount = plugin.getDataManager().getAccount(player.getName()) != null;

        // 进服欢迎语 (在登录 GUI 打开前显示)
        if (plugin.getTextManager() != null && plugin.getTextManager().isEnabled()) {
            plugin.getTextManager().sendWelcome(player);
        }

        if (registered) {
            if (authMeMode && !isLocalAccount) {
                plugin.sendMessage(player, "welcome-back-authme", player.getName());
            } else {
                plugin.sendMessage(player, "welcome-back", player.getName());
            }
        } else {
            plugin.sendMessage(player, "welcome-new", player.getName());
        }

        try {
            player.sendTitle(
                    plugin.getLangManager().getTitle(player, "title-main"),
                    plugin.getLangManager().getTitle(player,
                            registered ? "title-login" : "title-register"),
                    10, 60, 10);
        } catch (NoSuchMethodError ignored) {
        }

        // 延迟 1 秒显示规则并打开 GUI
        SchedulerUtil.runForEntityDelayed(plugin, player, () -> {
            if (player.isOnline() && !plugin.isAuthenticated(player)) {
                // 服务器规则 (打开登录 GUI 前显示)
                if (plugin.getTextManager() != null && plugin.getTextManager().isEnabled()) {
                    plugin.getTextManager().sendRules(player);
                }
                plugin.getGuiManager().openLoginGUI(player);
            }
        }, 20L);

        // 登录超时踢出 (在玩家所在区域线程，确保 kickPlayer 线程安全)
        int timeout = plugin.getLoginTimeoutSeconds();
        if (timeout > 0) {
            SchedulerUtil.runForEntityDelayed(plugin, player, () -> {
                if (player.isOnline() && !plugin.isAuthenticated(player)) {
                    String reason = plugin.getKickMessage(player, "kick-timeout");
                    player.kickPlayer(reason);
                }
            }, timeout * 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getAuthManager().logout(player);
        plugin.clearLoginFail(player);
        plugin.getGuiManager().restoreFlightSafety(player);
    }
}