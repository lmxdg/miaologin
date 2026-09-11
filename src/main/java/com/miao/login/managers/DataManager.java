package com.miao.login.managers;

import com.miao.login.MiaoLogin;
import com.miao.login.models.PlayerAccount;
import com.miao.login.utils.HashUtil;
import com.miao.login.utils.SchedulerUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 数据管理器 - 负责账户的持久化存储
 *
 * 支持两种后端 (由配置 storage.backend 决定):
 *   - yaml   (默认): accounts.yml 存储，所有数据内存缓存
 *   - sqlite       : data.db SQLite 数据库存储，所有数据内存缓存
 *
 * 注意: 所有写操作都会同步写入后端 + 异步刷新缓存
 */
public class DataManager {

    private final MiaoLogin plugin;
    // 内存缓存 (玩家名小写 -> 账户)，两种后端共用
    private final Map<String, PlayerAccount> accountsCache = new HashMap<>();

    // YAML 后端相关
    private File dataFile;
    private FileConfiguration dataConfig;

    // SQLite 后端
    private SQLiteBackend sqliteBackend;

    // 当前使用的后端: "yaml" 或 "sqlite"
    private String backendType;

    // AuthMe 桥接器
    private AuthMeBridge authMeBridge;

    public DataManager(MiaoLogin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化存储后端
     */
    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        backendType = plugin.getConfig().getString("storage.backend", "yaml").toLowerCase();
        if (!backendType.equals("yaml") && !backendType.equals("sqlite")) {
            plugin.getLogger().warning("未知存储后端: " + backendType + "，回退到 yaml 喵");
            backendType = "yaml";
        }

        accountsCache.clear();

        if (backendType.equals("sqlite")) {
            sqliteBackend = new SQLiteBackend(plugin);
            sqliteBackend.initialize();
            accountsCache.putAll(sqliteBackend.loadAll());
            plugin.getLogger().info("使用 SQLite 存储后端，已加载 "
                    + accountsCache.size() + " 个账户喵");
        } else {
            dataFile = new File(plugin.getDataFolder(), "accounts.yml");
            if (!dataFile.exists()) {
                try {
                    dataFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "无法创建 accounts.yml", e);
                }
            }
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            loadYamlToCache();
            plugin.getLogger().info("使用 YAML 存储后端，已加载 "
                    + accountsCache.size() + " 个账户喵");
        }
    }

    /**
     * 从 YAML 加载到内存缓存
     */
    private void loadYamlToCache() {
        if (dataConfig == null) return;
        ConfigurationSection root = dataConfig.getConfigurationSection("accounts");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;
                PlayerAccount account = new PlayerAccount();
                account.setUsername(sec.getString("username", key));
                account.setPasswordHash(sec.getString("passwordHash", ""));
                account.setSalt(sec.getString("salt", ""));
                account.setRegisterTime(sec.getLong("registerTime", 0));
                account.setLastLoginTime(sec.getLong("lastLoginTime", 0));
                account.setLastLoginIP(sec.getString("lastLoginIP", ""));
                accountsCache.put(key.toLowerCase(), account);
            }
        }
    }

    /**
     * 保存全部数据到 YAML 后端
     */
    private void saveYaml() {
        if (dataConfig == null) return;
        dataConfig.set("accounts", null);
        for (Map.Entry<String, PlayerAccount> entry : accountsCache.entrySet()) {
            String key = entry.getKey();
            PlayerAccount acc = entry.getValue();
            String path = "accounts." + key;
            dataConfig.set(path + ".username", acc.getUsername());
            dataConfig.set(path + ".passwordHash", acc.getPasswordHash());
            dataConfig.set(path + ".salt", acc.getSalt());
            dataConfig.set(path + ".registerTime", acc.getRegisterTime());
            dataConfig.set(path + ".lastLoginTime", acc.getLastLoginTime());
            dataConfig.set(path + ".lastLoginIP", acc.getLastLoginIP());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存 accounts.yml", e);
        }
    }

    /**
     * 保存全部数据到磁盘
     */
    public void save() {
        if ("sqlite".equals(backendType)) {
            // SQLite 模式下账户已在写入时即时保存，这里只需刷新所有缓存
            for (PlayerAccount acc : accountsCache.values()) {
                sqliteBackend.upsertAccount(acc);
            }
        } else {
            saveYaml();
        }
    }

    /**
     * 异步保存
     */
    public void saveAsync() {
        SchedulerUtil.runAsync(plugin, this::save);
    }

    /**
     * 保存单个账户 (立即写入后端)
     */
    private void persistAccount(PlayerAccount account) {
        if ("sqlite".equals(backendType)) {
            // SQLite 异步写入
            SchedulerUtil.runAsync(plugin, () -> sqliteBackend.upsertAccount(account));
        } else {
            // YAML 异步全量写入
            saveAsync();
        }
    }

    public int getAccountCount() {
        return accountsCache.size();
    }

    public void setAuthMeBridge(AuthMeBridge bridge) {
        this.authMeBridge = bridge;
    }

    public boolean isAuthMeTakeoverEnabled() {
        return authMeBridge != null && authMeBridge.isEnabled();
    }

    /**
     * 检查玩家是否已注册 (本地或 AuthMe)
     */
    public boolean isRegistered(String username) {
        if (accountsCache.containsKey(username.toLowerCase())) return true;
        if (authMeBridge != null && authMeBridge.isEnabled()) {
            return authMeBridge.isRegistered(username);
        }
        return false;
    }

    public PlayerAccount getAccount(String username) {
        return accountsCache.get(username.toLowerCase());
    }

    /**
     * 创建新账户
     */
    public PlayerAccount createAccount(String username, String plainPassword) {
        String salt = HashUtil.generateSalt();
        String hash = HashUtil.hashPassword(plainPassword, salt);
        PlayerAccount account = new PlayerAccount(username, hash, salt, System.currentTimeMillis());
        accountsCache.put(username.toLowerCase(), account);
        persistAccount(account);
        return account;
    }

    /**
     * 更新玩家密码
     */
    public boolean updatePassword(String username, String newPlainPassword) {
        PlayerAccount account = getAccount(username);
        if (account == null) return false;
        String salt = HashUtil.generateSalt();
        String hash = HashUtil.hashPassword(newPlainPassword, salt);
        account.setSalt(salt);
        account.setPasswordHash(hash);
        persistAccount(account);
        return true;
    }

    /**
     * 验证玩家密码 (本地优先，AuthMe 兜底)
     */
    public boolean checkPassword(String username, String plainPassword) {
        // 先查本地
        PlayerAccount account = getAccount(username);
        if (account != null) {
            return HashUtil.verifyPassword(plainPassword, account.getSalt(), account.getPasswordHash());
        }
        // 再查 AuthMe
        if (authMeBridge != null && authMeBridge.isEnabled()) {
            boolean ok = authMeBridge.checkPassword(username, plainPassword);
            if (ok) {
                authMeBridge.migrateAccount(this, username, plainPassword);
            }
            return ok;
        }
        return false;
    }

    /**
     * 更新最后登录信息
     */
    public void updateLoginInfo(String username, String ip) {
        PlayerAccount account = getAccount(username);
        if (account == null) return;
        account.setLastLoginTime(System.currentTimeMillis());
        account.setLastLoginIP(ip);
        persistAccount(account);
    }

    /**
     * 删除账户
     */
    public boolean deleteAccount(String username) {
        String key = username.toLowerCase();
        if (!accountsCache.containsKey(key)) return false;
        accountsCache.remove(key);
        if ("sqlite".equals(backendType)) {
            SchedulerUtil.runAsync(plugin, () -> sqliteBackend.deleteAccount(username));
        } else {
            saveAsync();
        }
        return true;
    }

    /**
     * 生成临时重置令牌
     */
    public String generateResetToken(String username) {
        PlayerAccount account = getAccount(username);
        if (account == null) return null;
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        if (dataConfig != null) {
            dataConfig.set("reset-tokens." + token, username.toLowerCase());
            try {
                dataConfig.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "无法保存重置令牌", e);
            }
        }
        return token;
    }

    public String getBackendType() {
        return backendType;
    }
}