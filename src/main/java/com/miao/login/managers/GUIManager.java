package com.miao.login.managers;

import com.miao.login.MiaoLogin;
import com.miao.login.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI 管理器 - 创建箱子界面用于登录/注册/重置密码
 *
 * 多语言: 所有文本走 LangManager，按玩家客户端语言显示
 *
 * 空中登录修复:
 *   - 玩家在空中下落时打开箱子界面会被客户端自动关闭，导致无法登录。
 *   - 本类在打开 GUI 前检测玩家是否离地，若是则临时开启飞行模式暂停下落，
 *     登录成功后再通过 restoreFlightSafety 恢复原始飞行状态。
 */
public class GUIManager {

    public static class MiaoLoginHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final MiaoLogin plugin;
    // 记录因空中登录保护而临时修改的 allowFlight 状态 (UUID -> 原状态)
    private final Map<UUID, Boolean> flightSafetyOriginal = new HashMap<>();

    public GUIManager(MiaoLogin plugin) {
        this.plugin = plugin;
    }

    /**
     * 打开主登录 GUI
     */
    public void openLoginGUI(Player player) {
        // 空中登录保护: 若玩家不在地面，临时开启飞行以暂停下落
        applyFlightSafety(player);

        boolean registered = plugin.getDataManager().isRegistered(player.getName());
        String lang = plugin.getLangManager().getLanguageFor(player);

        String title = plugin.getLangManager().get(lang, "gui-title");
        Inventory inv = Bukkit.createInventory(new MiaoLoginHolder(), 27, title);

        ItemStack border = createItem(getPaneMaterial(), " ", null);
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        if (registered) {
            ItemStack loginItem = createItem(Material.NAME_TAG,
                    plugin.getLangManager().get(lang, "gui-login-name"),
                    Arrays.asList(
                            plugin.getLangManager().get(lang, "gui-login-lore-1"),
                            plugin.getLangManager().get(lang, "gui-login-lore-2"),
                            "",
                            plugin.getLangManager().get(lang, "gui-login-lore-3")
                    ));
            inv.setItem(11, loginItem);

            ItemStack resetItem = createItem(Material.BARRIER,
                    plugin.getLangManager().get(lang, "gui-reset-name"),
                    Arrays.asList(
                            plugin.getLangManager().get(lang, "gui-reset-lore-1"),
                            plugin.getLangManager().get(lang, "gui-reset-lore-2"),
                            "",
                            plugin.getLangManager().get(lang, "gui-reset-lore-3")
                    ));
            inv.setItem(15, resetItem);
        } else {
            ItemStack registerItem = createItem(Material.BOOK,
                    plugin.getLangManager().get(lang, "gui-register-name"),
                    Arrays.asList(
                            plugin.getLangManager().get(lang, "gui-register-lore-1"),
                            plugin.getLangManager().get(lang, "gui-register-lore-2"),
                            plugin.getLangManager().get(lang, "gui-register-lore-3"),
                            "",
                            plugin.getLangManager().get(lang, "gui-register-lore-4")
                    ));
            inv.setItem(13, registerItem);
        }

        ItemStack infoItem = createItem(Material.PAPER,
                plugin.getLangManager().get(lang, "gui-info-name"),
                Arrays.asList(
                        plugin.getLangManager().get(lang, "gui-info-lore-1",
                                plugin.getMinPasswordLength(), plugin.getMaxPasswordLength()),
                        plugin.getLangManager().get(lang, "gui-info-lore-2"),
                        "",
                        plugin.getLangManager().get(lang, "gui-info-lore-3")
                ));
        inv.setItem(22, infoItem);

        player.openInventory(inv);
    }

    /**
     * 关闭玩家当前打开的 GUI
     */
    public void closeGUI(Player player) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getHolder() instanceof MiaoLoginHolder) {
            player.closeInventory();
        }
    }

    /**
     * 空中登录保护: 若玩家不在地面，临时开启飞行模式暂停下落，
     * 避免打开箱子 GUI 时被客户端自动关闭。
     */
    private void applyFlightSafety(Player player) {
        if (player.isOnGround()) {
            return;
        }
        // 已离地: 临时允许飞行并设置为飞行状态，停止下落
        if (!flightSafetyOriginal.containsKey(player.getUniqueId())) {
            flightSafetyOriginal.put(player.getUniqueId(), player.getAllowFlight());
        }
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    /**
     * 恢复空中登录保护前的飞行状态
     */
    public void restoreFlightSafety(Player player) {
        Boolean original = flightSafetyOriginal.remove(player.getUniqueId());
        if (original == null) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(original);
    }

    /**
     * 判断给定 Inventory 是否属于本插件
     */
    public static boolean isMiaoLoginInventory(Inventory inv) {
        return inv != null && inv.getHolder() instanceof MiaoLoginHolder;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name); // 已着色
            if (lore != null) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String line : lore) {
                    colored.add(line); // 已着色
                }
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material getPaneMaterial() {
        try {
            return Material.valueOf("GRAY_STAINED_GLASS_PANE");
        } catch (IllegalArgumentException e) {
            try {
                return Material.valueOf("THIN_GLASS");
            } catch (IllegalArgumentException e2) {
                return Material.GLASS;
            }
        }
    }
}