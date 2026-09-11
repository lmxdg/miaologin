package com.miao.login.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * 调度器工具 - 基于 BukkitScheduler 的统一调度封装
 *
 * 说明:
 *   自 v1.5.0 起，MiaoLogin 不再支持 Folia。
 *   本类保留原有方法签名以兼容已有调用代码，
 *   所有任务均通过 BukkitScheduler 在主线程或异步线程执行。
 *
 * 兼容 Spigot / Paper / Purpur 1.7-26.2
 */
public final class SchedulerUtil {

    private SchedulerUtil() {}

    /**
     * 在主线程执行任务 (与实体相关的操作)
     *
     * @param plugin 插件实例
     * @param entity 实体 (保留参数兼容旧调用，实际不使用)
     * @param task   要执行的任务
     */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * 在主线程延迟执行任务
     */
    public static void runForEntityDelayed(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    /**
     * 异步立即执行任务
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    /**
     * 异步延迟执行任务
     */
    public static void runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
    }

    /**
     * 在主线程执行任务 (用于无实体/位置的全局操作)
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * 在主线程延迟执行任务
     */
    public static void runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
}
