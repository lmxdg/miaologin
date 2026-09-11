package com.miao.login.managers;

import com.miao.login.MiaoLogin;
import com.miao.login.utils.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * 多语言管理器 (i18n)
 *
 * 支持语言:
 *   - en_US (英语)
 *   - zh_CN (简体中文, 默认)
 *   - zh_TW (繁体中文)
 *   - ja_JP (日语)
 *
 * 玩家客户端语言通过 Player.getLocale() 获取 (1.13+),
 * 老版本回退到默认语言。
 *
 * 消息中支持 {0}, {1}, ... 占位符, 通过 String.format 风格替换
 */
public class LangManager {

    private final MiaoLogin plugin;
    private final Map<String, FileConfiguration> languages = new HashMap<>();
    private String defaultLanguage;
    // 客户端语言代码前缀 -> 内部语言代码 的映射
    // 例如 "zh_cn" -> "zh_CN", "zh_tw" -> "zh_TW", "en_us" -> "en_US"
    private final Map<String, String> localeMap = new HashMap<>();

    public LangManager(MiaoLogin plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载所有语言文件
     */
    public void load() {
        defaultLanguage = plugin.getConfig().getString("language.default", "zh_CN");
        boolean autoDetect = plugin.getConfig().getBoolean("language.auto-detect", true);

        languages.clear();
        localeMap.clear();

        // 内置语言列表
        String[] builtIn = {"en_US", "zh_CN", "zh_TW", "ja_JP"};
        for (String lang : builtIn) {
            loadLanguage(lang);
        }

        // 加载用户自定义语言文件 (lang 目录下其他 yml)
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (langDir.exists() && langDir.isDirectory()) {
            File[] files = langDir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().substring(0, f.getName().length() - 4);
                    if (!languages.containsKey(name)) {
                        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                        languages.put(name, cfg);
                        plugin.getLogger().info("已加载自定义语言: " + name + " 喵");
                    }
                }
            }
        }

        // 构建客户端语言映射
        // Minecraft 客户端 locale 形如 "zh_cn", "en_us", "ja_jp"
        localeMap.put("zh_cn", "zh_CN");
        localeMap.put("zh_cn_#hans", "zh_CN");
        localeMap.put("zh_hans_cn", "zh_CN");
        localeMap.put("zh_tw", "zh_TW");
        localeMap.put("zh_hk", "zh_TW");
        localeMap.put("zh_hant", "zh_TW");
        localeMap.put("zh_hant_tw", "zh_TW");
        localeMap.put("zh_hant_hk", "zh_TW");
        localeMap.put("en_us", "en_US");
        localeMap.put("en_gb", "en_US");
        localeMap.put("en_au", "en_US");
        localeMap.put("en_ca", "en_US");
        localeMap.put("en_nz", "en_US");
        localeMap.put("ja_jp", "ja_JP");
        localeMap.put("ja", "ja_JP");

        // 默认语言不存在则回退到 zh_CN
        if (!languages.containsKey(defaultLanguage)) {
            plugin.getLogger().warning("默认语言 " + defaultLanguage
                    + " 不存在，回退到 zh_CN 喵");
            defaultLanguage = "zh_CN";
        }

        plugin.getLogger().info("多语言系统已加载，可用语言: "
                + String.join(", ", languages.keySet())
                + "，默认: " + defaultLanguage
                + "，自动检测客户端: " + autoDetect + " 喵");
    }

    /**
     * 加载单个语言文件 (优先从插件目录读，回退到 JAR 内置资源)
     */
    private void loadLanguage(String lang) {
        File file = new File(plugin.getDataFolder(), "lang" + File.separator + lang + ".yml");
        FileConfiguration cfg;

        // 优先用外部文件 (允许服主修改)
        if (file.exists()) {
            cfg = YamlConfiguration.loadConfiguration(file);
        } else {
            // 从 JAR 内置资源加载
            InputStream in = plugin.getResource("lang/" + lang + ".yml");
            if (in == null) {
                plugin.getLogger().warning("找不到语言文件: " + lang + " 喵");
                return;
            }
            cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            // 同时把内置文件保存到外部 (方便服主修改)
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                if (!file.exists()) {
                    plugin.saveResource("lang/" + lang + ".yml", false);
                }
            } catch (Exception ignored) {
            }
        }
        languages.put(lang, cfg);
    }

    /**
     * 根据玩家客户端语言获取对应语言代码
     */
    public String getLanguageFor(Player player) {
        if (player == null) return defaultLanguage;

        // 自动检测关闭时，用默认语言
        if (!plugin.getConfig().getBoolean("language.auto-detect", true)) {
            return defaultLanguage;
        }

        String clientLocale;
        try {
            // 1.13+ 使用 Player.getLocale()
            clientLocale = player.getLocale();
        } catch (NoSuchMethodError e) {
            // 老版本回退
            return defaultLanguage;
        }

        if (clientLocale == null || clientLocale.isEmpty()) {
            return defaultLanguage;
        }

        // 转小写规范化
        String lower = clientLocale.toLowerCase(Locale.ROOT);

        // 精确匹配
        if (localeMap.containsKey(lower)) {
            return localeMap.get(lower);
        }

        // 模糊匹配 (前缀)
        for (Map.Entry<String, String> entry : localeMap.entrySet()) {
            if (lower.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 兜底: 如果客户端语言代码与某语言文件名小写匹配
        for (String lang : languages.keySet()) {
            if (lang.toLowerCase(Locale.ROOT).equals(lower)) return lang;
            // 取前2位匹配 (例如 "es" 匹配 "es_ES")
            if (lower.length() >= 2 && lang.toLowerCase(Locale.ROOT).startsWith(lower.substring(0, 2))) {
                return lang;
            }
        }

        return defaultLanguage;
    }

    /**
     * 获取消息 (带占位符替换)
     *
     * @param lang  语言代码
     * @param key   消息键
     * @param args  占位符参数 (替换 {0}, {1}, ...)
     * @return 已着色已替换的消息
     */
    public String get(String lang, String key, Object... args) {
        String message = rawGet(lang, key);
        message = replacePlaceholders(message, args);
        message = applyNekoSuffix(message);
        return ColorUtil.color(message);
    }

    /**
     * 根据配置决定是否移除消息末尾的喵尾音
     */
    private String applyNekoSuffix(String message) {
        if (message == null || message.isEmpty()) return message;
        if (!plugin.isRemoveNekoSuffix()) return message;

        String result = message;
        // 移除末尾的 "喵"、"喵~"、"meow"、"meow."、"nya" 等尾音 (忽略大小写)
        String[] suffixes = {"喵~", "喵", "meow.", "meow", "nya~", "nya"};
        boolean changed;
        do {
            changed = false;
            String trimmed = result.trim();
            for (String suffix : suffixes) {
                if (trimmed.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                    trimmed = trimmed.substring(0, trimmed.length() - suffix.length()).trim();
                    changed = true;
                    break;
                }
            }
            result = trimmed;
        } while (changed);

        return result;
    }

    /**
     * 获取原始消息 (未着色)
     */
    private String rawGet(String lang, String key) {
        FileConfiguration cfg = languages.get(lang);
        if (cfg != null) {
            String msg = cfg.getString(key);
            if (msg != null) return msg;
        }
        // 回退到默认语言
        if (!lang.equals(defaultLanguage)) {
            FileConfiguration def = languages.get(defaultLanguage);
            if (def != null) {
                String msg = def.getString(key);
                if (msg != null) return msg;
            }
        }
        // 最后回退到 key 本身
        return key;
    }

    /**
     * 替换 {0}, {1}, ... 占位符
     */
    private String replacePlaceholders(String message, Object... args) {
        if (message == null) return "";
        if (args == null || args.length == 0) return message;
        String result = message;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            String value = args[i] == null ? "" : args[i].toString();
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * 给玩家发送消息 (自动选择语言)
     */
    public void sendMessage(Player player, String key, Object... args) {
        String lang = getLanguageFor(player);
        String prefix = get(lang, "prefix");
        String message = get(lang, key, args);
        player.sendMessage(prefix + message);
    }

    /**
     * 给玩家发送不带前缀的消息
     */
    public void sendRawMessage(Player player, String key, Object... args) {
        String lang = getLanguageFor(player);
        String message = get(lang, key, args);
        player.sendMessage(message);
    }

    /**
     * 给命令发送者发送消息 (使用默认语言)
     */
    public void send(CommandSender sender, String key, Object... args) {
        String prefix = get(defaultLanguage, "prefix");
        String message = get(defaultLanguage, key, args);
        sender.sendMessage(prefix + message);
    }

    /**
     * 给命令发送者发送不带前缀的消息
     */
    public void sendRaw(CommandSender sender, String key, Object... args) {
        String message = get(defaultLanguage, key, args);
        sender.sendMessage(message);
    }

    /**
     * 获取踢出消息 (用于 kickPlayer)
     */
    public String getKickMessage(Player player, String key, Object... args) {
        String lang = getLanguageFor(player);
        return get(lang, key, args);
    }

    /**
     * 获取标题/副标题 (用于 sendTitle)
     */
    public String getTitle(Player player, String key, Object... args) {
        String lang = getLanguageFor(player);
        return get(lang, key, args);
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public java.util.Set<String> getAvailableLanguages() {
        return languages.keySet();
    }
}