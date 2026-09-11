package com.miao.login.managers;

import com.miao.login.MiaoLogin;
import com.miao.login.models.PlayerAccount;
import com.miao.login.utils.HashUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * SQLite 数据库存储后端
 *
 * 用于替换 YAML 存储，性能更好且支持并发。
 * 表结构:
 *   accounts(
 *     username      TEXT PRIMARY KEY,    -- 玩家名小写
 *     display_name  TEXT,                -- 原始玩家名
 *     password_hash TEXT NOT NULL,       -- 密码哈希
 *     salt          TEXT NOT NULL,       -- 盐值
 *     register_time INTEGER,             -- 注册时间戳
 *     last_login_time INTEGER,           -- 最后登录时间戳
 *     last_login_ip  TEXT                -- 最后登录 IP
 *   )
 *
 * 连接策略: 每次操作从 DriverManager 获取新连接并关闭，
 * 避免长连接老化问题 (SQLite 连接开销很小)。
 */
public class SQLiteBackend {

    private final MiaoLogin plugin;
    private final String dbPath;
    private final String tableName = "accounts";

    public SQLiteBackend(MiaoLogin plugin) {
        this.plugin = plugin;
        // 数据库文件路径: plugins/MiaoLogin/data.db
        this.dbPath = new File(plugin.getDataFolder(), "data.db").getAbsolutePath();
    }

    /**
     * 初始化数据库 (建表)
     */
    public void initialize() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                            "username TEXT PRIMARY KEY," +
                            "display_name TEXT," +
                            "password_hash TEXT NOT NULL," +
                            "salt TEXT NOT NULL," +
                            "register_time INTEGER," +
                            "last_login_time INTEGER," +
                            "last_login_ip TEXT" +
                            ")");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_accounts_username ON " +
                            tableName + "(username)");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "初始化 SQLite 数据库失败喵", e);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "未找到 SQLite JDBC 驱动喵", e);
        }
    }

    private Connection openConnection() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * 加载所有账户到内存缓存
     */
    public Map<String, PlayerAccount> loadAll() {
        Map<String, PlayerAccount> map = new HashMap<>();
        String sql = "SELECT username, display_name, password_hash, salt, " +
                "register_time, last_login_time, last_login_ip FROM " + tableName;
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                PlayerAccount acc = new PlayerAccount();
                String username = rs.getString("display_name");
                if (username == null) username = rs.getString("username");
                acc.setUsername(username);
                acc.setPasswordHash(rs.getString("password_hash"));
                acc.setSalt(rs.getString("salt"));
                acc.setRegisterTime(rs.getLong("register_time"));
                acc.setLastLoginTime(rs.getLong("last_login_time"));
                acc.setLastLoginIP(rs.getString("last_login_ip") == null
                        ? "" : rs.getString("last_login_ip"));
                map.put(rs.getString("username"), acc);
            }
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "加载数据库账户失败喵", e);
        }
        return map;
    }

    /**
     * 保存或更新账户
     */
    public void upsertAccount(PlayerAccount account) {
        String sql = "INSERT OR REPLACE INTO " + tableName +
                " (username, display_name, password_hash, salt, register_time, last_login_time, last_login_ip) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, account.getUsername().toLowerCase());
            stmt.setString(2, account.getUsername());
            stmt.setString(3, account.getPasswordHash());
            stmt.setString(4, account.getSalt());
            stmt.setLong(5, account.getRegisterTime());
            stmt.setLong(6, account.getLastLoginTime());
            stmt.setString(7, account.getLastLoginIP());
            stmt.executeUpdate();
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "保存账户失败: " + account.getUsername() + " 喵", e);
        }
    }

    /**
     * 删除账户
     */
    public boolean deleteAccount(String username) {
        String sql = "DELETE FROM " + tableName + " WHERE username = ?";
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            return stmt.executeUpdate() > 0;
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "删除账户失败: " + username + " 喵", e);
            return false;
        }
    }

    /**
     * 查询账户数量
     */
    public int countAccounts() {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "查询账户数失败喵", e);
        }
        return 0;
    }
}