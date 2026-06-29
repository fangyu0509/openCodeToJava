package io.opencode.core.plugin;

import io.opencode.core.tool.Tool;
import io.opencode.core.session.Message;
import io.opencode.core.session.Session;

import java.util.List;
import java.util.Map;

/**
 * 插件接口，提供插件化扩展能力。
 * 插件可注册工具、监听会话生命周期事件（开始、消息、工具执行、工具结果、错误等）。
 * 所有事件回调默认为空实现，插件只需覆盖需要的方法。
 */
public interface Plugin {
    String name();                          // 插件名称
    String version();                       // 插件版本
    List<Tool<?>> tools();                  // 插件注册的工具列表
    default void onLoad() {}                // 插件加载时回调
    default void onUnload() {}              // 插件卸载时回调
    default void onSessionStart(Session session) {}  // 会话开始时回调
    default void onMessage(Session session, Message message) {}  // 收到消息时回调
    default void onToolExecute(String toolId, Map<String, Object> args) {}  // 工具即将执行时回调
    default void onToolResult(String toolId, String output) {}  // 工具执行完成后回调
    default void onError(Session session, String error) {}  // 发生错误时回调
}
