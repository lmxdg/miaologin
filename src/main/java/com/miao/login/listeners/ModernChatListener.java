package com.miao.login.listeners;

import com.miao.login.MiaoLogin;
import com.miao.login.managers.AuthManager;
import com.miao.login.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * 现代 (Paper 1.16+) 聊天事件监听器
 *
 * 背景:
 *   - Paper 1.16+ 使用 io.papermc.paper.event.player.AsyncChatEvent 取代
 *     传统的 org.bukkit.event.player.AsyncPlayerChatEvent。
 *   - 在 Paper 上，AsyncPlayerChatEvent 默认不再触发 (或行为不一致)，
 *     导致 MiaoLogin 无法捕获玩家在聊天框输入的密码。
 *
 * 解决方案:
 *   - 由于编译期依赖 (spigot-api 1.13.2) 中不包含 AsyncChatEvent，
 *     本类通过反射在运行时动态注册事件监听器 (EventExecutor)。
 *   - 当 AsyncChatEvent 触发时，提取玩家输入的消息，取消事件，
 *     并委托 PlayerChatListener.handlePasswordInput 进行处理。
 *
 * 注意:
 *   - 当本监听器成功注册时 (Paper)，将不再注册 PlayerChatListener
 *     以避免重复处理同一密码输入。
 */
public class ModernChatListener {

    /** Paper 现代聊天事件的全限定类名 */
    private static final String ASYNC_CHAT_EVENT_CLASS =
            "io.papermc.paper.event.player.AsyncChatEvent";

    private final MiaoLogin plugin;
    private final PlayerChatListener chatListener;
    private Listener registeredListener;
    private boolean registered = false;

    public ModernChatListener(MiaoLogin plugin, PlayerChatListener chatListener) {
        this.plugin = plugin;
        this.chatListener = chatListener;
    }

    /**
     * 检测当前服务器是否支持 AsyncChatEvent (Paper 1.16+)
     */
    public static boolean isAvailable() {
        try {
            Class.forName(ASYNC_CHAT_EVENT_CLASS, false,
                    ModernChatListener.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 通过反射注册 AsyncChatEvent 监听器
     *
     * @return true 注册成功，false 当前服务器不支持或注册失败 (应回退到 PlayerChatListener)
     */
    public boolean register() {
        if (registered) return true;
        if (!isAvailable()) return false;

        try {
            Class<?> eventClass = Class.forName(ASYNC_CHAT_EVENT_CLASS);

            // 使用 EventExecutor 通过反射处理事件 (无需在编译期依赖 AsyncChatEvent)
            EventExecutor executor = (listener, event) -> handleEvent(event);

            // 占位 Listener 实例 (EventExecutor 模式不需要在 Listener 上加 @EventHandler)
            registeredListener = new Listener() {};

            PluginManager pm = Bukkit.getPluginManager();
            // 调用 registerEvent(Class<? extends Event> event, Listener listener,
            //                    EventPriority priority, EventExecutor executor,
            //                    Plugin plugin, boolean ignoreCancelled)
            Method registerMethod = pm.getClass().getMethod(
                    "registerEvent",
                    Class.class,
                    Listener.class,
                    EventPriority.class,
                    EventExecutor.class,
                    org.bukkit.plugin.Plugin.class,
                    boolean.class);

            registerMethod.invoke(pm,
                    eventClass,
                    registeredListener,
                    EventPriority.LOWEST,
                    executor,
                    plugin,
                    true);

            registered = true;
            plugin.getLogger().info("已注册 AsyncChatEvent 监听器 (Paper 现代聊天事件) 喵");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "注册 AsyncChatEvent 失败，将回退到 AsyncPlayerChatEvent 喵", e);
            return false;
        }
    }

    /**
     * 处理 AsyncChatEvent
     *
     * 流程:
     *   1. 通过反射获取 player 和 message
     *   2. 取消事件 (防止消息广播)
     *   3. 切换到主线程后调用 handlePasswordInput
     */
    private void handleEvent(Event event) {
        try {
            // 1. 获取玩家
            Method getPlayer = event.getClass().getMethod("getPlayer");
            Object playerObj = getPlayer.invoke(event);
            if (!(playerObj instanceof Player)) return;
            Player player = (Player) playerObj;

            // 2. 已登录玩家不处理 (允许其消息正常发送)
            if (plugin.isAuthenticated(player)) {
                return;
            }

            // 3. 检查 pending 状态
            AuthManager authManager = plugin.getAuthManager();
            AuthManager.PendingState pending = authManager.getPending(player);

            if (pending == null || pending.action == AuthManager.PendingAction.NONE) {
                // 未登录且没有 pending 流程 - 取消消息并提示
                cancelEvent(event);
                plugin.sendMessage(player, "login-required-chat");
                plugin.sendMessage(player, "login-required-action");
                return;
            }

            // 4. 取消事件 (不让消息广播出去，密码不能泄露)
            cancelEvent(event);

            // 5. 提取消息内容
            String message = extractMessage(event);
            if (message == null) {
                plugin.getLogger().warning("无法提取 AsyncChatEvent 消息内容，玩家: " + player.getName() + " 喵");
                return;
            }

            // 6. 切换到主线程处理密码输入
            final String msg = message;
            SchedulerUtil.runForEntity(plugin, player,
                    () -> chatListener.handlePasswordInput(player, msg));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "处理 AsyncChatEvent 失败喵", e);
        }
    }

    /**
     * 取消事件 (反射调用 setCancelled(true))
     */
    private void cancelEvent(Event event) {
        try {
            Method setCancelled = event.getClass().getMethod("setCancelled", boolean.class);
            setCancelled.invoke(event, Boolean.TRUE);
        } catch (Exception e) {
            // 某些事件可能不可取消，忽略
        }
    }

    /**
     * 从 AsyncChatEvent 提取消息文本
     *
     * AsyncChatEvent.message() 返回 net.kyori.adventure.text.Component，
     * 由于编译期没有 Adventure API，需要通过反射转换为纯文本:
     *   1. 优先调用 plainMessage() (Paper 1.19.4+ 直接返回 String)
     *   2. 否则调用 originalMessage() 获取原始 Component
     *   3. 否则调用 message() 拿到 Component，再尝试:
     *      a. Component.plainText() (Adventure 4.10+)
     *      b. Component.content() (仅 TextComponent，最常见)
     *      c. 从 Component.toString() 中按 "content=" 正则提取
     *      d. toString() 兜底
     */
    private String extractMessage(Event event) {
        String result = null;

        // 1. 优先尝试 plainMessage() (Paper 1.19.4+ 直接返回 String)
        try {
            Method plainMessage = event.getClass().getMethod("plainMessage");
            Object obj = plainMessage.invoke(event);
            if (obj instanceof String) {
                result = (String) obj;
            } else if (obj != null) {
                result = obj.toString();
            }
        } catch (NoSuchMethodException ignored) {
            // 老版本 Paper 没有，继续尝试
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINEST, "plainMessage() 不可用喵", e);
        }
        if (isUsable(result)) return result.trim();

        // 2. 调用 originalMessage() 获取原始 Component (Paper 1.19+)
        Object component = null;
        try {
            Method originalMethod = event.getClass().getMethod("originalMessage");
            component = originalMethod.invoke(event);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINEST, "originalMessage() 不可用喵", e);
        }

        // 3. 如果没有 originalMessage() 或返回 null，回退到 message()
        if (component == null) {
            try {
                Method messageMethod = event.getClass().getMethod("message");
                component = messageMethod.invoke(event);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "无法获取 AsyncChatEvent.message() 喵", e);
                return null;
            }
        }

        if (component == null) return null;

        // 3a. Component.plainText()
        try {
            Method plainText = component.getClass().getMethod("plainText");
            Object obj = plainText.invoke(component);
            if (obj instanceof String) {
                result = (String) obj;
            } else if (obj != null) {
                result = obj.toString();
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINEST, "Component.plainText() 不可用喵", e);
        }
        if (isUsable(result)) return result.trim();

        // 3b. Component.content() (仅适用于 TextComponent)
        try {
            Method content = component.getClass().getMethod("content");
            Object obj = content.invoke(component);
            if (obj instanceof String) {
                result = (String) obj;
            } else if (obj != null) {
                result = obj.toString();
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINEST, "Component.content() 不可用喵", e);
        }
        if (isUsable(result)) return result.trim();

        // 3c. 兜底: 从 toString 中按 "content=" 提取，避免拿到类名
        String raw = component.toString();
        if (raw != null) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("content=([^,}]+)");
            java.util.regex.Matcher matcher = pattern.matcher(raw);
            if (matcher.find()) {
                result = matcher.group(1);
                if (isUsable(result)) return result.trim();
            }
        }

        // 3d. 最后的兜底
        return raw != null ? raw.trim() : null;
    }

    /**
     * 判断提取到的字符串是否可用 (非 null、非空、不是纯类名)
     */
    private boolean isUsable(String str) {
        if (str == null) return false;
        String trimmed = str.trim();
        if (trimmed.isEmpty()) return false;
        // 排除 Component 的类名/实现名
        return !trimmed.contains("TextComponent") && !trimmed.contains("Component");
    }
}
