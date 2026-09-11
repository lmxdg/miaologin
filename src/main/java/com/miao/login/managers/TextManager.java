package com.miao.login.managers;

import com.miao.login.MiaoLogin;
import com.miao.login.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

/**
 * 进服消息管理器 - 加载 text.yml 中的欢迎语和服务器规则
 *
 * v1.6 新增:
 *   - 玩家加入服务器时自动显示可配置的欢迎语
 *   - 打开登录 GUI 前自动显示可配置的服务器规则
 *   - 所有文字均可在 plugins/MiaoLogin/text.yml 中修改
 */
public class TextManager {

    private final MiaoLogin plugin;
    private final File file;
    private FileConfiguration config;

    private boolean enabled;
    private List<String> welcomeMessages;
    private List<String> ruleMessages;

    public TextManager(MiaoLogin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "text.yml");
    }

    /**
     * 加载/重载 text.yml
     */
    public void load() {
        if (!file.exists()) {
            plugin.saveResource("text.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        enabled = config.getBoolean("enabled", true);
        welcomeMessages = config.getStringList("welcome");
        ruleMessages = config.getStringList("rules");

        // 如果列表为空，使用默认提示
        if (welcomeMessages == null || welcomeMessages.isEmpty()) {
            welcomeMessages = java.util.Collections.singletonList(
                    "&b欢迎来到服务器喵！");
        }
        if (ruleMessages == null || ruleMessages.isEmpty()) {
            ruleMessages = java.util.Collections.singletonList(
                    "&7请遵守服务器规则喵~");
        }
    }

    /**
     * 重载配置
     */
    public void reload() {
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 向玩家发送欢迎语
     */
    public void sendWelcome(Player player) {
        if (!enabled) return;
        for (String line : welcomeMessages) {
            player.sendMessage(ColorUtil.color(applyNekoSuffix(line)));
        }
    }

    /**
     * 向玩家发送服务器规则
     */
    public void sendRules(Player player) {
        if (!enabled) return;
        for (String line : ruleMessages) {
            player.sendMessage(ColorUtil.color(applyNekoSuffix(line)));
        }
    }

    /**
     * 根据配置决定是否移除消息末尾的喵尾音
     */
    private String applyNekoSuffix(String message) {
        if (message == null || message.isEmpty()) return message;
        if (!plugin.isRemoveNekoSuffix()) return message;

        String result = message;
        String[] suffixes = {"喵~", "喵", "meow.", "meow", "nya~", "nya"};
        boolean changed;
        do {
            changed = false;
            String trimmed = result.trim();
            for (String suffix : suffixes) {
                if (trimmed.toLowerCase(java.util.Locale.ROOT).endsWith(suffix.toLowerCase(java.util.Locale.ROOT))) {
                    trimmed = trimmed.substring(0, trimmed.length() - suffix.length()).trim();
                    changed = true;
                    break;
                }
            }
            result = trimmed;
        } while (changed);

        return result;
    }

    public List<String> getWelcomeMessages() {
        return welcomeMessages;
    }

    public List<String> getRuleMessages() {
        return ruleMessages;
    }
}
