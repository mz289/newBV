package dev.frost819.newbv.app.ui.component.player.menu.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.app.data.DanmakuBlockHitStats
import dev.frost819.newbv.app.network.DanmakuBlockServer
import dev.frost819.newbv.app.ui.component.search.SoftKeyboard
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.danmaku.filter.DanmakuBlockFilter
import io.github.g0dkar.qrcode.QRCode

/** 正则符号补充键位，供添加规则对话框的软键盘渲染。 */
private val symbolKeyRows: List<List<String>> =
    listOf(
        listOf("/", ".", "*", "+", "?", "("),
        listOf(")", "[", "]", "^", "$", "|"),
    )

/**
 * 弹幕屏蔽管理面板（弹幕设置菜单"屏蔽"子项的值面板）。
 *
 * 从上到下：屏蔽总开关、添加规则入口、已有规则列表。
 * 规则行点击切换启用/停用，右侧按钮删除；"添加规则"打开
 * [DanmakuBlockAddDialog]（内含类型选择与 TV 软键盘）。
 *
 * @param modifier 修饰符
 * @param blockEnabled 屏蔽总开关
 * @param rules 屏蔽规则列表
 * @param onEnabledChange 总开关变化回调
 * @param onRulesChange 规则列表整体变化回调（切换/删除/添加统一走整体替换）
 * @param onFocusBackToParent 按方向右键返回子项列表的回调
 */
@Composable
fun DanmakuBlockPanel(
    modifier: Modifier = Modifier,
    blockEnabled: Boolean,
    rules: List<DanmakuBlockRule>,
    onEnabledChange: (Boolean) -> Unit,
    onRulesChange: (List<DanmakuBlockRule>) -> Unit,
    onFocusBackToParent: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    val totalHits by DanmakuBlockHitStats.totalHits.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .padding(vertical = 64.dp)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        if (listOf(Key.Enter, Key.DirectionCenter).contains(it.key)) {
                            return@onPreviewKeyEvent false
                        }
                        return@onPreviewKeyEvent true
                    }
                    val result = it.key == Key.DirectionRight
                    if (result) onFocusBackToParent()
                    result
                },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuListItem(
            text = if (blockEnabled) "屏蔽：开" else "屏蔽：关",
            selected = blockEnabled,
            onClick = { onEnabledChange(!blockEnabled) },
        )
        MenuListItem(
            text = "添加规则",
            selected = false,
            onClick = { showAddDialog = true },
        )
        MenuListItem(
            text = "远程管理",
            selected = false,
            onClick = { showQrDialog = true },
        )
        if (blockEnabled && totalHits > 0) {
            RuleHintText(text = "本会话已屏蔽 $totalHits 条弹幕（切集清零）")
        }
        if (rules.isEmpty()) {
            RuleHintText(text = "暂无规则")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(rules) { index, rule ->
                    DanmakuBlockRuleItem(
                        rule = rule,
                        onToggle = {
                            onRulesChange(
                                rules.mapIndexed { i, r ->
                                    if (i == index) r.copy(enabled = !r.enabled) else r
                                },
                            )
                        },
                        onDelete = {
                            onRulesChange(rules.filterIndexed { i, _ -> i != index })
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        DanmakuBlockAddDialog(
            existingRules = rules,
            onAdd = { rule ->
                onRulesChange(rules + rule)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showQrDialog) {
        DanmakuBlockQrDialog(onDismiss = { showQrDialog = false })
    }
}

/**
 * 远程管理二维码弹窗。
 *
 * 打开期间启动本地管理服务器（随机端口，仅局域网），
 * 手机扫码或输入地址即可管理屏蔽规则（增删改/导入导出），
 * 修改与电视端实时双向同步；关闭弹窗即停止服务器。
 *
 * @param onDismiss 关闭回调
 */
@Composable
private fun DanmakuBlockQrDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    var showUrlText by remember { mutableStateOf(false) }

    // 弹窗生命周期即服务器生命周期：进入启动、离开停止
    DisposableEffect(Unit) {
        DanmakuBlockServer.start(context)
        url = DanmakuBlockServer.getUrl()
        onDispose { DanmakuBlockServer.stop() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                colors =
                    androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "远程管理弹幕屏蔽",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(220.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        val qrImage =
                            url?.let {
                                remember(it) { generateQrImage(it) }
                            }
                        if (qrImage != null) {
                            Image(
                                modifier = Modifier.size(200.dp),
                                bitmap = qrImage,
                                contentDescription = "管理页二维码",
                            )
                        } else {
                            Text(
                                text = "正在启动服务器…",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (showUrlText) {
                        Text(
                            text = url ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.tv.material3.Button(
                            modifier =
                                Modifier.touchClickable(onClick = { showUrlText = !showUrlText }),
                            onClick = { showUrlText = !showUrlText },
                        ) {
                            Text(if (showUrlText) "隐藏地址" else "显示地址")
                        }
                        androidx.tv.material3.Button(
                            modifier = Modifier.touchClickable(onClick = onDismiss),
                            onClick = onDismiss,
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

/** 生成二维码图片，失败返回 null。 */
private fun generateQrImage(content: String): ImageBitmap? =
    runCatching {
        val graphics = QRCode(content).render()
        val bitmap = graphics.nativeImage() as android.graphics.Bitmap
        bitmap.asImageBitmap()
    }.getOrNull()

/** 面板内说明文字。 */
@Composable
private fun RuleHintText(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 4.dp),
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 单条屏蔽规则行：点击切换启用/停用，右侧按钮删除。
 */
@Composable
private fun DanmakuBlockRuleItem(
    rule: DanmakuBlockRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .touchClickable(onClick = onToggle),
            onClick = onToggle,
            shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
            colors = ControlFocusDefaults.surfaceColors(),
            border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(4.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.type.displayLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (rule.enabled) "启用中" else "已停用",
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (rule.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                Text(
                    text = rule.value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            modifier =
                Modifier
                    .size(38.dp)
                    .touchClickable(onClick = onDelete),
            onClick = onDelete,
            shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
            colors = ControlFocusDefaults.surfaceColors(),
            border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(4.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "删除规则",
                )
            }
        }
    }
}

/** 规则类型展示名。 */
private fun DanmakuBlockRuleType.displayLabel(): String =
    when (this) {
        DanmakuBlockRuleType.Keyword -> "关键词"
        DanmakuBlockRuleType.Regex -> "正则"
        DanmakuBlockRuleType.User -> "用户"
        DanmakuBlockRuleType.Color -> "颜色"
    }

/**
 * 添加屏蔽规则对话框。
 *
 * 左侧 TV 软键盘（含正则符号补充键位），右侧类型选择、输入预览与操作按钮。
 * 添加时校验：空白值拒绝；正则类型实时校验合法性；与已有规则（类型+值相同）去重。
 *
 * @param existingRules 已有规则（用于去重）
 * @param onAdd 添加成功回调（携带新规则）
 * @param onDismiss 关闭回调
 */
@Composable
private fun DanmakuBlockAddDialog(
    existingRules: List<DanmakuBlockRule>,
    onAdd: (DanmakuBlockRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedType by remember { mutableStateOf(DanmakuBlockRuleType.Keyword) }
    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val keyboardFirstKeyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { keyboardFirstKeyFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                colors =
                    androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoftKeyboard(
                        firstButtonFocusRequester = keyboardFirstKeyFocusRequester,
                        extraRows = symbolKeyRows,
                        onSearchLabel = "添加",
                        onClick = { input += it },
                        onClear = { input = "" },
                        onDelete = {
                            if (input.isNotEmpty()) input = input.dropLast(1)
                        },
                        onSearch = {
                            val value = input.trim()
                            when {
                                value.isEmpty() -> {}
                                existingRules.any { it.type == selectedType && it.value.trim() == value } ->
                                    errorMessage = "规则已存在"

                                selectedType == DanmakuBlockRuleType.Regex &&
                                    DanmakuBlockFilter.parseBlockRegex(value) == null ->
                                    errorMessage = "正则表达式无效"

                                selectedType == DanmakuBlockRuleType.Color && !value.isHexColor() ->
                                    errorMessage = "颜色须为 6 位十六进制（如 FF6699）"

                                else ->
                                    onAdd(
                                        DanmakuBlockRule(
                                            type = selectedType,
                                            value =
                                                if (selectedType == DanmakuBlockRuleType.Color) {
                                                    value.removePrefix("#").uppercase()
                                                } else {
                                                    value
                                                },
                                        ),
                                    )
                            }
                        },
                    )

                    Column(
                        modifier = Modifier.width(320.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "添加屏蔽规则",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DanmakuBlockRuleType.entries.forEach { type ->
                                BlockTypeChip(
                                    label = type.displayLabel(),
                                    selected = selectedType == type,
                                    onClick = {
                                        selectedType = type
                                        errorMessage = null
                                    },
                                )
                            }
                        }
                        Text(
                            text =
                                when (selectedType) {
                                    DanmakuBlockRuleType.Keyword -> "包含关键词即屏蔽（忽略大小写）"
                                    DanmakuBlockRuleType.Regex ->
                                        "正则表达式，可用 /pattern/ 包裹；默认忽略大小写"
                                    DanmakuBlockRuleType.User -> "发送者 midHash（十六进制串）或数字 UID"
                                    DanmakuBlockRuleType.Color -> "弹幕颜色，6 位十六进制（如 FF6699）"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            modifier = Modifier.height(52.dp),
                            shape = MaterialTheme.shapes.small,
                            colors =
                                androidx.tv.material3.SurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Box(contentAlignment = Alignment.CenterStart) {
                                Text(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    text = input.ifEmpty { "用左侧键盘输入…" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        if (input.isEmpty()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        errorMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 颜色规则值是否为合法 6 位十六进制（兼容可选 # 前缀）。 */
private fun String.isHexColor(): Boolean {
    val value = removePrefix("#")
    return value.length == 6 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}

/**
 * 规则类型选择块。
 */
@Composable
private fun BlockTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(4.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
