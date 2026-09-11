package com.miao.login.managers;

import com.miao.login.MiaoLogin;
import com.miao.login.utils.AuthMeHashUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * AuthMe 桥接器 - 接管 AuthMe 登录
 *
 * 工作原理:
 * 1. 检测服务器是否安装了 AuthMe 插件
 * 2. 自动连接 AuthMe 的 SQLite 数据库 (plugins/AuthMe/authme.db)
 * 3. 提供账户查询和密码验证接口
 * 4. 玩家首次登录成功后，账户会被迁移到 MiaoLogin 本地存储
 *
 * 支持 AuthMe 默认的 SHA256 哈希格式 ($SHA$salt$hash)
 * 也兼容 MD5、纯 SHA256、明文等格式
 */
public class AuthMeBridge {

    private final MiaoLogin plugin;
    private boolean enabled = false;
    private String databasePath;
    private Connection connection;
    // 缓存: 玩家名小写 -> 密码哈希 (避免每次登录都查库)
    private final Map<String, String> hashCache = new HashMap<>();

    public AuthMeBridge(MiaoLogin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化桥接器，检测 AuthMe 是否存在并连接数据库
     *
     * @return true 表示接管模式已启用
     */
    public boolean initialize() {
        // 1. 检测 AuthMe 插件
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe == null) {
            // 也检测常见变体名
            authMe = Bukkit.getPluginManager().getPlugin("authme");
            if (authMe == null) {
                plugin.getLogger().info("未检测到 AuthMe 插件，跳过接管模式喵");
                return false;
            }
        }
        plugin.getLogger().info("检测到 AuthMe 插件 (" + authMe.getDescription().getVersion()
                + ")，启用接管模式喵");

        // 2. 定位 AuthMe 数据库文件
        File authMeDb = new File(authMe.getDataFolder(), "authme.db");
        if (!authMeDb.exists()) {
            // 尝试其他可能的文件名
            File[] candidates = authMe.getDataFolder().listFiles(
                    (dir, name) -> name.endsWith(".db") || name.endsWith(".sqlite"));
            if (candidates != null && candidates.length > 0) {
                authMeDb = candidates[0];
            } else {
                plugin.getLogger().warning("未找到 AuthMe 数据库文件，接管模式无法启用喵");
                return false;
            }
        }
        this.databasePath = authMeDb.getAbsolutePath();

        // 3. 加载 SQLite 驱动 (Bukkit 服务器通常自带)
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.WARNING,
                    "未找到 SQLite JDBC 驱动，无法读取 AuthMe 数据库喵", e);
            return false;
        }

        // 4. 测试连接并预加载所有账户哈希
        try {
            connect();
            int count = preloadHashes();
            plugin.getLogger().info("已从 AuthMe 数据库加载 " + count + " 个账户哈希喵");
            enabled = true;

            // 5. 如果配置允许，禁用 AuthMe 插件本身，避免它继续处理登录/发送消息
            if (plugin.isAuthMeTakeoverDisablePlugin()) {
                disableAuthMePlugin(authMe);
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "连接 AuthMe 数据库失败，接管模式未启用喵", e);
            return false;
        }
    }

    /**
     * 禁用 AuthMe 插件本身，防止它继续监听事件、注册命令、向玩家发送消息
     */
    private void disableAuthMePlugin(Plugin authMe) {
        try {
            Bukkit.getPluginManager().disablePlugin(authMe);
            plugin.getLogger().info("已自动禁用 AuthMe 插件，MiaoLogin 将全权接管登录喵");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "禁用 AuthMe 插件时出错，可能需要手动卸载 AuthMe 喵", e);
        }
    }

    /**
     * 连接 SQLite 数据库
     */
    private void connect() throws SQLException {
        if (connection != null) {
            try {
                if (!connection.isClosed()) return;
            } catch (SQLException ignored) {
            }
        }
        String url = "jdbc:sqlite:" + databasePath;
        connection = DriverManager.getConnection(url);
    }

    /**
     * 预加载所有账户的密码哈希到内存缓存
     */
    private int preloadHashes() throws SQLException {
        hashCache.clear();
        connect();
        // AuthMe 默认表名是 "authme"
        String sql = "SELECT username, password FROM authme";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    String username = rs.getString(1);
                    String password = rs.getString(2);
                    if (username != null && password != null) {
                        hashCache.put(username.toLowerCase(), password);
                        count++;
                    }
                }
                return count;
            }
        } catch (SQLException e) {
            // 表名可能不是 "authme"，尝试探测
            plugin.getLogger().warning("查询 authme 表失败，尝试探测表名喵");
            return probeAndPreload();
        }
    }

    /**
     * 探测数据库表名并预加载
     */
    private int probeAndPreload() throws SQLException {
        connect();
        // 查询 SQLite master 表获取所有表名
        String tableSql = "SELECT name FROM sqlite_master WHERE type='table'";
        try (PreparedStatement stmt = connection.prepareStatement(tableSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String tableName = rs.getString(1);
                if (tableName == null) continue;
                // 尝试从这个表查询 username 和 password 字段
                try {
                    String sql = "SELECT username, password FROM " + tableName;
                    try (PreparedStatement q = connection.prepareStatement(sql);
                         ResultSet qr = q.executeQuery()) {
                        int count = 0;
                        boolean hasData = false;
                        while (qr.next()) {
                            hasData = true;
                            String username = qr.getString(1);
                            String password = qr.getString(2);
                            if (username != null && password != null) {
                                hashCache.put(username.toLowerCase(), password);
                                count++;
                            }
                        }
                        if (hasData) {
                            plugin.getLogger().info("找到 AuthMe 数据表: " + tableName + " 喵");
                            return count;
                        }
                    }
                } catch (SQLException ignored) {
                    // 这个表没有 username/password 字段，继续尝试下一个
                }
            }
        }
        return 0;
    }

    /**
     * 玩家是否在 AuthMe 数据库中已注册
     */
    public boolean isRegistered(String username) {
        if (!enabled) return false;
        return hashCache.containsKey(username.toLowerCase());
    }

    /**
     * 验证玩家密码是否与 AuthMe 数据库中存储的哈希匹配
     */
    public boolean checkPassword(String username, String plainPassword) {
        if (!enabled) return false;
        String storedHash = hashCache.get(username.toLowerCase());
        if (storedHash == null) return false;
        return AuthMeHashUtil.verify(plainPassword, storedHash);
    }

    /**
     * 迁移玩家账户到 MiaoLogin 本地存储
     * (登录成功后调用，这样后续验证就不需要再查 AuthMe)
     *
     * @return true 表示账户是从 AuthMe 迁移过来的
     */
    public boolean migrateAccount(DataManager localDataManager, String username, String plainPassword) {
        if (!isRegistered(username)) return false;
        // 如果本地还没有这个账户，就创建一个
        if (!localDataManager.isRegistered(username)) {
            localDataManager.createAccount(username, plainPassword);
            plugin.getLogger().info("已将玩家 " + username + " 的账户从 AuthMe 迁移到本地喵");
            return true;
        }
        return false;
    }

    /**
     * 关闭数据库连接
     */
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
        hashCache.clear();
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMigratedAccountCount() {
        return hashCache.size();
    }
}