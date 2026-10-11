package dev.frost819.newbv.app.network

/**
 * 远程控制台功能标签。
 *
 * 每个功能注册一个底部导航标签（[label] + [iconPath] + [panelHtml] 面板），
 * 以及若干动作处理器：手机页面统一 `POST /action?do=<id>:<动作>`，
 * 请求 body 原样透传给处理器，返回文本作为响应回传页面渲染。
 *
 * 约定：
 * - [panelHtml] 为面板内层 HTML 片段（可含 <style>/<script> 块，服务端整页
 *   拼装因此脚本能正常执行）；片段内元素 id 与 JS 函数必须以 [id] 前缀命名，
 *   避免多功能同页冲突
 * - 与服务端交互统一使用页面共享助手 remotePost(action, body)
 */
open class RemoteConsoleFeature(
    val id: String,
    val label: String,
    val iconPath: String,
    val panelHtml: String,
    val actions: Map<String, suspend (String) -> String>,
) {
    /**
     * 分发动作。
     *
     * @param action 动作名（不含功能前缀）。
     * @param body 原始请求 body（可能为空串）。
     * @return 处理结果文本；动作不存在返回 null。
     */
    suspend fun dispatch(
        action: String,
        body: String,
    ): String? = actions[action]?.invoke(body)
}
