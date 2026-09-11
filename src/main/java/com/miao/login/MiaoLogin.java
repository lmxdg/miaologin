package com.miao.login;

import com.miao.login.commands.MiaoLoginCommand;
import com.miao.login.listeners.InventoryClickListener;
import com.miao.login.listeners.ModernChatListener;
import com.miao.login.listeners.PlayerChatListener;
import com.miao.login.listeners.PlayerJoinListener;
import com.miao.login.listeners.PlayerRestrictListener;
import com.miao.login.managers.AuthManager;
import com.miao.login.managers.AuthMeBridge;
import com.miao.login.managers.DataManager;
import com.miao.login.managers.GUIManager;
import com.miao.login.managers.LangManager;
import com.miao.login.managers.TextManager;
import com.miao.login.utils.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MiaoLogin 插件主类 v1.8
 *
 * 兼容 Spigot / Paper / Purpur 1.7-26.2
 * 功能: 注册 / 登录 / 重置密码 (箱子 GUI + 聊天输入)
 *       - 多语言 (英/繁中/简中/日)，按客户端语言自动选择
 *       - 登录超时自动踢出
 *       - 密码错误次数上限自动踢出
 *       - SQLite / YAML 双后端存储
 *       - AuthMe 自动接管
 *       - 支持 Paper 1.16+ 的 AsyncChatEvent
 *       - v1.5: 移除 Folia 支持，回归 BukkitScheduler
 *       - v1.6: 修复空中无法登录问题，增加进服欢迎语和服务器规则
 *       - v1.7: 接管 AuthMe 后自动禁用 AuthMe 插件，避免重复触发和消息干扰
 *       - v1.8: 新增去除喵尾音功能 (neko-suffix.remove，默认关闭)
 */
public class MiaoLogin extends JavaPlugin {

    /** 插件版本号 */
    public static final String VERSION = "1.8";

    private DataManager dataManager;
    private AuthManager authManager;
    private GUIManager guiManager;
    private AuthMeBridge authMeBridge;
    private LangManager langManager;
    private TextManager textManager;

    // 密码错误计数器 (玩家 UUID -> 错误次数)
    private final Map<UUID, Integer> loginFailCount = new HashMap<>();

    // 配置项
    private int minPasswordLength;
    private int maxPasswordLength;
    private int loginTimeoutSeconds;
    private int maxLoginFailures;
    private boolean hideChatInput;
    private boolean blockCommandsBeforeLogin;
    private List<String> allowedCommandsBeforeLogin;
    private String messagePrefix;
    private boolean authMeTakeoverConfigEnabled;
    private boolean authMeTakeoverActive;
    private boolean authMeTakeoverDisablePlugin;
    private boolean removeNekoSuffix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        // 1. 先初始化多语言 (其他模块可能用到)
        langManager = new LangManager(this);
        langManager.load();

        // 2. 数据管理器
        dataManager = new DataManager(this);
        dataManager.load();

        // 3. 认证和 GUI
        authManager = new AuthManager(this, dataManager);
        guiManager = new GUIManager(this);

        // 4. 进服消息 (text.yml)
        textManager = new TextManager(this);
        textManager.load();

        // 5. AuthMe 接管检测
        initializeAuthMeTakeover();

        // 6. 事件监听器
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        // 聊天监听器: 优先使用 Paper 1.16+ 的 AsyncChatEvent
        // - Paper 1.16+ 使用 io.papermc.paper.event.player.AsyncChatEvent
        // - 传统 Spigot (1.7-1.15) 使用 AsyncPlayerChatEvent
        // 二者择一注册，避免重复处理同一密码输入
        PlayerChatListener legacyChatListener = new PlayerChatListener(this);
        boolean modernRegistered = false;
        if (ModernChatListener.isAvailable()) {
            ModernChatListener modernChatListener = new ModernChatListener(this, legacyChatListener);
            modernRegistered = modernChatListener.register();
        }
        if (!modernRegistered) {
            // 回退到传统的 AsyncPlayerChatEvent
            getServer().getPluginManager().registerEvents(legacyChatListener, this);
            getLogger().info("使用传统 AsyncPlayerChatEvent 监听器喵");
        }
        getServer().getPluginManager().registerEvents(new PlayerRestrictListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);

        // 7. 命令
        MiaoLoginCommand cmd = new MiaoLoginCommand(this);
        getCommand("miaologin").setExecutor(cmd);
        getCommand("miaologin").setTabCompleter(cmd);

        // 启动日志
        printAsciiArt();
        getLogger().info("MiaoLogin 登录插件已经启动了喵，版本 " + VERSION);
        getLogger().info("兼容 Spigot/Paper/Purpur 1.7-26.2 喵");
        getLogger().info("欢迎加入猫猫的交流群：1041398736 喵");
        getLogger().info("存储后端: " + dataManager.getBackendType() + " 喵");
        getLogger().info("可用语言: " + String.join(", ", langManager.getAvailableLanguages())
                + "，默认: " + langManager.getDefaultLanguage() + " 喵");
        if (authMeTakeoverActive && authMeBridge != null) {
            getLogger().info("已启用 AuthMe 接管模式喵! 玩家可用原 AuthMe 密码登录喵");
            getLogger().info("AuthMe 账户数: " + authMeBridge.getMigratedAccountCount() + " 喵");
        } else {
            getLogger().info("AuthMe 接管模式未启用 (未检测到 AuthMe 或配置关闭) 喵");
        }
        getLogger().info("本地账户数: " + dataManager.getAccountCount() + " 喵");
        getLogger().info("登录超时: " + loginTimeoutSeconds + " 秒，最大密码错误次数: "
                + maxLoginFailures + " 喵");
    }

    private void initializeAuthMeTakeover() {
        authMeBridge = new AuthMeBridge(this);
        boolean ok = authMeBridge.initialize();
        if (ok) {
            authMeTakeoverActive = true;
            dataManager.setAuthMeBridge(authMeBridge);
        } else {
            authMeBridge = null;
            authMeTakeoverActive = false;
        }
    }

    private void printAsciiArt() {
        String[] art = {
                "&d  _   _      _         _       _         ",
                "&d | \\ | |    | |       | |     (_)        ",
                "&d |  \\| | ___| | _____ | |      _  _ __   ",
                "&d | . ` |/ _ \\ |/ / _ \\| |     | || '_ \\  ",
                "&d | |\\  |  __/   < (_) | |____ | || | | | ",
                "&d |_| \\_|\\___|_|\\_\\___/\\_____/ |_||_| |_| ",
                "&d                                          ",
                "&5          MiaoLogin v" + VERSION + " 喵~             "
        };
        for (String line : art) {
            getLogger().info(ColorUtil.color(line));
        }
    }

    @Override
    public void onDisable() {
        if (authMeBridge != null) {
            authMeBridge.shutdown();
        }
        if (dataManager != null) {
            dataManager.save();
        }
        getLogger().info("MiaoLogin 已禁用喵");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadConfigValues();
        if (langManager != null) {
            langManager.load();
        }
        if (textManager != null) {
            textManager.reload();
        }
    }

    private void loadConfigValues() {
        FileConfiguration cfg = getConfig();
        minPasswordLength = cfg.getInt("password.min-length", 4);
        maxPasswordLength = cfg.getInt("password.max-length", 32);
        loginTimeoutSeconds = cfg.getInt("login.timeout-seconds", 120);
        maxLoginFailures = cfg.getInt("login.max-failures", 3);
        hideChatInput = cfg.getBoolean("login.hide-chat-input", true);
        blockCommandsBeforeLogin = cfg.getBoolean("restrictions.block-commands", true);
        allowedCommandsBeforeLogin = cfg.getStringList("restrictions.allowed-commands");
        messagePrefix = cfg.getString("messages.prefix", "&d[&5MiaoLogin&d] &r");
        authMeTakeoverConfigEnabled = cfg.getBoolean("authme-takeover.enabled", true);
        authMeTakeoverDisablePlugin = cfg.getBoolean("authme-takeover.disable-authme-plugin", true);
        removeNekoSuffix = cfg.getBoolean("neko-suffix.remove", false);
    }

    // ========== 配置访问器 ==========
    public int getMinPasswordLength() { return minPasswordLength; }
    public int getMaxPasswordLength() { return maxPasswordLength; }
    public int getLoginTimeoutSeconds() { return loginTimeoutSeconds; }
    public int getMaxLoginFailures() { return maxLoginFailures; }
    public boolean isHideChatInput() { return hideChatInput; }
    public boolean isBlockCommandsBeforeLogin() { return blockCommandsBeforeLogin; }
    public List<String> getAllowedCommandsBeforeLogin() { return allowedCommandsBeforeLogin; }
    public DataManager getDataManager() { return dataManager; }
    public AuthManager getAuthManager() { return authManager; }
    public GUIManager getGuiManager() { return guiManager; }
    public AuthMeBridge getAuthMeBridge() { return authMeBridge; }
    public LangManager getLangManager() { return langManager; }
    public TextManager getTextManager() { return textManager; }
    public boolean isAuthMeTakeoverEnabled() { return authMeTakeoverActive; }
    public boolean isAuthMeTakeoverConfigEnabled() { return authMeTakeoverConfigEnabled; }
    public boolean isAuthMeTakeoverDisablePlugin() { return authMeTakeoverDisablePlugin; }
    public boolean isRemoveNekoSuffix() { return removeNekoSuffix; }

    // ========== 密码错误计数 ==========

    /**
     * 获取玩家当前密码错误次数
     */
    public int getLoginFailCount(Player player) {
        return loginFailCount.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 增加密码错误计数，返回增加后的次数
     */
    public int incrementLoginFail(Player player) {
        int count = getLoginFailCount(player) + 1;
        loginFailCount.put(player.getUniqueId(), count);
        return count;
    }

    /**
     * 重置玩家密码错误计数 (登录成功或离开时调用)
     */
    public void resetLoginFail(Player player) {
        loginFailCount.remove(player.getUniqueId());
    }

    /**
     * 玩家离开时清理计数
     */
    public void clearLoginFail(Player player) {
        loginFailCount.remove(player.getUniqueId());
    }

    /**
     * 给玩家发送消息 (走多语言，自动选择客户端语言)
     */
    public void sendMessage(Player player, String key, Object... args) {
        langManager.sendMessage(player, key, args);
    }

    /**
     * 给玩家发送不带前缀的消息 (走多语言)
     */
    public void sendRawMessage(Player player, String key, Object... args) {
        langManager.sendRawMessage(player, key, args);
    }

    /**
     * 给命令发送者发送消息 (使用默认语言)
     */
    public void sendMessage(CommandSender sender, String key, Object... args) {
        langManager.send(sender, key, args);
    }

    /**
     * 给命令发送者发送不带前缀的消息 (使用默认语言)
     */
    public void sendRawMessage(CommandSender sender, String key, Object... args) {
        langManager.sendRaw(sender, key, args);
    }

    /**
     * 获取踢出消息 (走多语言)
     */
    public String getKickMessage(Player player, String key, Object... args) {
        return langManager.getKickMessage(player, key, args);
    }

    public boolean isAuthenticated(Player player) {
        return authManager.isLoggedIn(player);
    }
}