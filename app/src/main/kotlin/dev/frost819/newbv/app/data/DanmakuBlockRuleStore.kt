package dev.frost819.newbv.app.data

import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 弹幕屏蔽规则共享仓库（单一事实源）。
 *
 * TV 端设置菜单与手机网页管理端（本地 HTTP 服务器）都经由本仓库读写
 * 屏蔽开关与规则列表：所有写操作同步更新 [state] 并持久化到 Prefs，
 * [DanmakuViewModel] 与播放器 UI 通过 collect [state] 感知任意一端的变更，
 * 双端修改实时互相同步，无需额外网络层。
 */
object DanmakuBlockRuleStore {
    /** 屏蔽配置状态。 */
    data class State(
        val enabled: Boolean = false,
        val rules: List<DanmakuBlockRule> = emptyList(),
    )

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<State> = _state.asStateFlow()

    /** 从 Prefs 读取初始状态。 */
    private fun loadInitialState(): State =
        State(
            enabled = Prefs.danmakuBlockEnabled,
            rules = Prefs.danmakuBlockRules.decodeDanmakuBlockRules(),
        )

    /** 整体更新屏蔽开关与规则（值无变化时不触发发射）。 */
    fun setBlockState(
        enabled: Boolean,
        rules: List<DanmakuBlockRule>,
    ) {
        val current = _state.value
        if (current.enabled == enabled && current.rules == rules) return
        Prefs.danmakuBlockEnabled = enabled
        Prefs.danmakuBlockRules = rules.encodeDanmakuBlockRules()
        _state.value = State(enabled = enabled, rules = rules)
    }

    /** 更新屏蔽总开关。 */
    fun setEnabled(enabled: Boolean) = setBlockState(enabled, _state.value.rules)

    /** 整体替换规则列表。 */
    fun setRules(rules: List<DanmakuBlockRule>) = setBlockState(_state.value.enabled, rules)

    /**
     * 添加规则；与已有规则（类型 + 值，关键词忽略大小写）重复时返回 false。
     */
    fun addRule(rule: DanmakuBlockRule): Boolean {
        if (findRuleIndex(rule.type, rule.value) >= 0) return false
        setRules(_state.value.rules + rule)
        return true
    }

    /**
     * 批量添加规则（导入屏蔽串用）：与已有规则及本批内部去重（关键词忽略大小写）。
     *
     * @return 实际新增条数
     */
    fun addRules(rules: List<DanmakuBlockRule>): Int {
        if (rules.isEmpty()) return 0
        val existing = _state.value.rules.toMutableList()
        var imported = 0
        rules.forEach { rule ->
            val duplicate =
                existing.any { r ->
                    r.type == rule.type &&
                        r.value.equals(rule.value, ignoreCase = r.type == DanmakuBlockRuleType.Keyword)
                }
            if (!duplicate) {
                existing.add(rule)
                imported++
            }
        }
        if (imported > 0) setRules(existing)
        return imported
    }

    /** 切换指定规则的启用状态；规则不存在返回 false。 */
    fun toggleRule(
        type: DanmakuBlockRuleType,
        value: String,
    ): Boolean {
        val index = findRuleIndex(type, value)
        if (index < 0) return false
        setRules(
            _state.value.rules.mapIndexed { i, r ->
                if (i == index) r.copy(enabled = !r.enabled) else r
            },
        )
        return true
    }

    /** 删除指定规则；规则不存在返回 false。 */
    fun removeRule(
        type: DanmakuBlockRuleType,
        value: String,
    ): Boolean {
        val index = findRuleIndex(type, value)
        if (index < 0) return false
        setRules(_state.value.rules.filterIndexed { i, _ -> i != index })
        return true
    }

    /** 按类型 + 值查找规则下标（关键词忽略大小写判等），找不到返回 -1。 */
    private fun findRuleIndex(
        type: DanmakuBlockRuleType,
        value: String,
    ): Int =
        _state.value.rules.indexOfFirst { rule ->
            rule.type == type && rule.value.equals(value, ignoreCase = rule.type == DanmakuBlockRuleType.Keyword)
        }
}
