package com.miao.login.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本颜色工具类 - 处理 & 颜色代码
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    /**
     * 将 & 颜色代码转换为 ChatColor
     */
    public static String color(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * 批量转换字符串列表的颜色代码
     */
    public static List<String> colorList(List<String> messages) {
        List<String> result = new ArrayList<>();
        if (messages == null) return result;
        for (String msg : messages) {
            result.add(color(msg));
        }
        return result;
    }

    /**
     * 移除所有颜色代码
     */
    public static String stripColor(String message) {
        if (message == null) return "";
        return ChatColor.stripColor(message);
    }
}