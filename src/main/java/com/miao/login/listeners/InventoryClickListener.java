package com.miao.login.listeners;

import com.miao.login.MiaoLogin;
import com.miao.login.managers.AuthManager;
import com.miao.login.managers.GUIManager;
import com.miao.login.utils.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GUI 点击/关闭监听器
 *
 * 关键修复:
 * - 监听 InventoryCloseEvent，当未登录玩家按 ESC 关闭登录界面时，
 *   延迟重新打开 GUI，防止玩家无法再次登录。
 * - 使用 scheduledReopen 集合标记"主动关闭等待重开"的状态，
 *   避免与点击按钮后的主动 closeInventory() 形成递归。
 */
public class InventoryClickListener implements Listener {

    private final MiaoLogin plugin;
    // 标记: 这些玩家是点击按钮主动关闭 GUI 的，不需要立即重开
    private final Set<UUID> manualClose = new HashSet<>();
    // 标记: 这些玩家已经调度了重开任务，避免重复调度
    private final Set<UUID> scheduledReopen = new HashSet<>();

    public InventoryClickListener(MiaoLogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!GUIManager.isMiaoLoginInventory(top)) return;

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        int topSize = top.getSize();
        if (rawSlot < 0 || rawSlot >= topSize) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = rawSlot;

        if (plugin.isAuthenticated(player)) {
            player.closeInventory();
            return;
        }

        boolean registered = plugin.getDataManager().isRegistered(player.getName());
        AuthManager authManager = plugin.getAuthManager();

        if (registered) {
            if (slot == 11) {
                // 登录: 标记为主动关闭，避免 InventoryCloseEvent 立即重开
                markManualClose(player);
                authManager.setPending(player, AuthManager.PendingAction.LOGIN_PASSWORD);
                player.closeInventory();
                plugin.sendMessage(player, "login-prompt");
            } else if (slot == 15) {
                markManualClose(player);
                authManager.setPending(player, AuthManager.PendingAction.RESET_OLD_PASSWORD);
                player.closeInventory();
                plugin.sendMessage(player, "reset-old-prompt");
            }
        } else {
            if (slot == 13) {
                markManualClose(player);
                authManager.setPending(player, AuthManager.PendingAction.REGISTER_PASSWORD);
                player.closeInventory();
                plugin.sendMessage(player, "register-prompt");
                plugin.sendMessage(player, "register-prompt-length",
                        plugin.getMinPasswordLength(), plugin.getMaxPasswordLength());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (GUIManager.isMiaoLoginInventory(top)) {
            event.setCancelled(true);
        }
    }

    /**
     * 监听 GUI 关闭事件
     *
     * 处理逻辑:
     * 1. 如果玩家已登录 - 不处理
     * 2. 如果玩家在"输入密码"流程中 (pending != NONE) - 不重开 GUI，让他在聊天框输入
     *    但提示玩家可以输入 cancel 重新打开菜单
     * 3. 如果玩家是按 ESC 关闭且没有 pending 流程 - 延迟 1 秒后重新打开 GUI
     * 4. 如果是主动关闭 (markManualClose) - 清除标志，不重开
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!GUIManager.isMiaoLoginInventory(top)) return;

        if (!(event.getPlayer() instanceof Player)) return;
        final Player player = (Player) event.getPlayer();

        // 已登录 - 不处理
        if (plugin.isAuthenticated(player)) return;

        // 主动关闭 (点击按钮后) - 清除标志，不重开
        if (manualClose.remove(player.getUniqueId())) {
            return;
        }

        // 已经调度了重开 - 不重复调度
        if (scheduledReopen.contains(player.getUniqueId())) {
            return;
        }

        AuthManager.PendingState pending = plugin.getAuthManager().getPending(player);
        boolean hasPending = pending != null && pending.action != AuthManager.PendingAction.NONE;

        if (hasPending) {
            // 玩家正在输入密码流程中 - 不重开 GUI，但给提示
            plugin.sendMessage(player, "pending-reminder");
            plugin.sendMessage(player, "pending-reminder-cancel");
            return;
        }

        // 玩家按 ESC 关闭了 GUI，且没有 pending 流程 - 延迟重开
        scheduledReopen.add(player.getUniqueId());
        SchedulerUtil.runForEntityDelayed(plugin, player, () -> {
            scheduledReopen.remove(player.getUniqueId());
            // 玩家仍然在线、未登录、且仍然没有 pending 流程时才重开
            if (player.isOnline()
                    && !plugin.isAuthenticated(player)
                    && !manualClose.contains(player.getUniqueId())) {
                AuthManager.PendingState p = plugin.getAuthManager().getPending(player);
                if (p == null || p.action == AuthManager.PendingAction.NONE) {
                    plugin.getGuiManager().openLoginGUI(player);
                }
            }
        }, 20L); // 1 秒后重开
    }

    /**
     * 标记玩家为"主动关闭 GUI"，避免 InventoryCloseEvent 触发重开
     */
    private void markManualClose(Player player) {
        manualClose.add(player.getUniqueId());
    }
}