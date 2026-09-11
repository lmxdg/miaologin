package com.miao.login.commands;

import com.miao.login.MiaoLogin;
import com.miao.login.models.PlayerAccount;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 管理命令: /miaologin
 *
 * 多语言: 控制台/管理员看到的消息走默认语言 (配置项 language.default)
 */
public class MiaoLoginCommand implements CommandExecutor, TabCompleter {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final MiaoLogin plugin;

    public MiaoLoginCommand(MiaoLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                return handleReload(sender);
            case "info":
                return handleInfo(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "unregister":
                return handleUnregister(sender, args);
            case "list":
                return handleList(sender, args);
            case "takeover":
                return handleTakeover(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                plugin.sendMessage(sender, "unknown-command", sub);
                return true;
        }
    }

    private boolean handleTakeover(CommandSender sender) {
        if (!sender.hasPermission("miaologin.admin.takeover")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        plugin.sendMessage(sender, "cmd-takeover-header");
        if (plugin.isAuthMeTakeoverEnabled()) {
            plugin.sendMessage(sender, "cmd-takeover-on");
            plugin.sendMessage(sender, "cmd-takeover-authme-count",
                    plugin.getAuthMeBridge().getMigratedAccountCount());
            plugin.sendMessage(sender, "cmd-takeover-local-count",
                    plugin.getDataManager().getAccountCount());
            plugin.sendMessage(sender, "cmd-takeover-hint");
        } else {
            plugin.sendMessage(sender, "cmd-takeover-off");
            plugin.sendMessage(sender, "cmd-takeover-reason");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("miaologin.admin.reload")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        plugin.reloadPluginConfig();
        plugin.sendMessage(sender, "cmd-reload");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("miaologin.admin.info")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, "cmd-usage-info");
            return true;
        }
        String name = args[1];
        PlayerAccount acc = plugin.getDataManager().getAccount(name);
        if (acc == null) {
            plugin.sendMessage(sender, "cmd-not-registered", name);
            return true;
        }
        plugin.sendMessage(sender, "cmd-info-header");
        plugin.sendMessage(sender, "cmd-info-name", acc.getUsername());
        plugin.sendMessage(sender, "cmd-info-register-time", formatDate(acc.getRegisterTime()));
        plugin.sendMessage(sender, "cmd-info-last-login", formatDate(acc.getLastLoginTime()));
        plugin.sendMessage(sender, "cmd-info-last-ip", acc.getLastLoginIP());

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.isOnline()) {
            boolean authed = plugin.isAuthenticated(Bukkit.getPlayerExact(name));
            plugin.sendMessage(sender, authed ? "cmd-info-status-online" : "cmd-info-status-not-login");
        } else {
            plugin.sendMessage(sender, "cmd-info-status-offline");
        }
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("miaologin.admin.reset")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, "cmd-usage-reset");
            return true;
        }
        String name = args[1];
        if (!plugin.getDataManager().isRegistered(name)) {
            plugin.sendMessage(sender, "cmd-not-registered", name);
            return true;
        }
        String tempPassword = generateRandomPassword(8);
        plugin.getDataManager().updatePassword(name, tempPassword);
        plugin.sendMessage(sender, "cmd-reset-done", name);
        plugin.sendMessage(sender, "cmd-reset-password", tempPassword);
        plugin.sendMessage(sender, "cmd-reset-notice");

        if (Bukkit.getPlayerExact(name) != null) {
            org.bukkit.entity.Player target = Bukkit.getPlayerExact(name);
            plugin.getAuthManager().logout(target);
            plugin.sendMessage(target, "cmd-reset-notify");
            plugin.getGuiManager().openLoginGUI(target);
        }
        return true;
    }

    private boolean handleUnregister(CommandSender sender, String[] args) {
        if (!sender.hasPermission("miaologin.admin.unregister")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, "cmd-usage-unregister");
            return true;
        }
        String name = args[1];
        if (!plugin.getDataManager().isRegistered(name)) {
            plugin.sendMessage(sender, "cmd-not-registered", name);
            return true;
        }
        plugin.getDataManager().deleteAccount(name);
        plugin.sendMessage(sender, "cmd-unregister-done", name);

        if (Bukkit.getPlayerExact(name) != null) {
            org.bukkit.entity.Player target = Bukkit.getPlayerExact(name);
            plugin.getAuthManager().logout(target);
            plugin.sendMessage(target, "cmd-unregister-notify");
            plugin.getGuiManager().openLoginGUI(target);
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("miaologin.admin.list")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        int total = plugin.getDataManager().getAccountCount();
        plugin.sendMessage(sender, "cmd-list-total", total);
        plugin.sendMessage(sender, "cmd-list-detail");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.sendRawMessage(sender, "cmd-help-header");
        plugin.sendRawMessage(sender, "cmd-help-help");
        if (sender.hasPermission("miaologin.admin.reload")) {
            plugin.sendRawMessage(sender, "cmd-help-reload");
        }
        if (sender.hasPermission("miaologin.admin.info")) {
            plugin.sendRawMessage(sender, "cmd-help-info");
        }
        if (sender.hasPermission("miaologin.admin.reset")) {
            plugin.sendRawMessage(sender, "cmd-help-reset");
        }
        if (sender.hasPermission("miaologin.admin.unregister")) {
            plugin.sendRawMessage(sender, "cmd-help-unregister");
        }
        if (sender.hasPermission("miaologin.admin.list")) {
            plugin.sendRawMessage(sender, "cmd-help-list");
        }
        if (sender.hasPermission("miaologin.admin.takeover")) {
            plugin.sendRawMessage(sender, "cmd-help-takeover");
        }
        plugin.sendRawMessage(sender, "cmd-help-footer");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "help", "reload", "info", "reset", "unregister", "list", "takeover"));
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(prefix)) result.add(s);
            }
            Collections.sort(result);
            return result;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("info") || sub.equals("reset") || sub.equals("unregister")) {
                List<String> names = new ArrayList<>();
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.add(p.getName());
                    }
                }
                return names;
            }
        }
        return Collections.emptyList();
    }

    private String formatDate(long timestamp) {
        if (timestamp <= 0) return plugin.getLangManager().get(
                plugin.getLangManager().getDefaultLanguage(), "cmd-no-record");
        return DATE_FORMAT.format(new Date(timestamp));
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}