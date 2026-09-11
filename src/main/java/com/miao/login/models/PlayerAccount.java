package com.miao.login.models;

/**
 * 玩家账户数据模型
 */
public class PlayerAccount {

    private String username;
    private String passwordHash;
    private String salt;
    private long registerTime;
    private long lastLoginTime;
    private String lastLoginIP;

    public PlayerAccount() {
    }

    public PlayerAccount(String username, String passwordHash, String salt, long registerTime) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.registerTime = registerTime;
        this.lastLoginTime = 0;
        this.lastLoginIP = "";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public long getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(long registerTime) {
        this.registerTime = registerTime;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(long lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public String getLastLoginIP() {
        return lastLoginIP;
    }

    public void setLastLoginIP(String lastLoginIP) {
        this.lastLoginIP = lastLoginIP;
    }
}