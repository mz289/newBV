package dev.frost819.newbv.app.ui.screen.user

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.R
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.viewmodel.user.UserSwitchViewModel
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.db.entity.UserEntity
import kotlinx.coroutines.launch

/**
 * 账号管理页面。
 *
 * 显示所有已登录的账户列表，支持：
 * - 点击切换账户
 * - 添加新账户（跳转登录页）
 * - 删除账户
 * - 设置/取消双账号解析账号（星标，指定账号仅用于播放地址解析以解锁会员权益）
 * - 导出账号凭证（JSON 文件，用于迁移到其他设备或作为 Cookie 登录备份）
 *
 * 从登录页返回时自动刷新用户列表。
 *
 * @param viewModel 账号管理 ViewModel。
 * @param onNavigateLogin 跳转登录页回调。
 */
@Composable
fun UserSwitchScreen(
    modifier: Modifier = Modifier,
    viewModel: UserSwitchViewModel = hiltViewModel(),
    onNavigateLogin: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusSaver = rememberFocusSaver()
    focusSaver.RestoreFocus()

    // 待导出凭证的账号 UID（CreateDocument 回调时无法携带数据，暂存于组合状态）
    var exportUid by remember { mutableStateOf(0L) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                scope.launch {
                    viewModel.exportAuthJson(exportUid)?.let { json ->
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(json.toByteArray())
                            }
                        }.onFailure { Loggers.get("UserSwitchScreen").error(it) { "export auth json failed" } }
                    }
                }
            }
        }

    LifecycleResumeEffect(Unit) {
        viewModel.updateData()
        onPauseOrDispose { }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.user_switch_title),
                style = MaterialTheme.typography.displayMedium,
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.login_requesting))
                }
            } else if (uiState.users.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.user_switch_no_users),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateLogin,
                            modifier =
                                Modifier
                                    .focusSaverItem(focusSaver, "add_user_empty")
                                    .touchClickable(onClick = onNavigateLogin),
                            shape = ButtonDefaults.shape(shape = ControlFocusDefaults.shape),
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                            colors = ControlFocusDefaults.buttonColors(),
                            border = ControlFocusDefaults.buttonBorder(),
                        ) {
                            Text(text = stringResource(R.string.user_switch_add))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(8.dp),
                ) {
                    items(uiState.users, key = { it.uid }) { user ->
                        UserListItem(
                            user = user,
                            isCurrentUser = user.uid == uiState.currentUid,
                            isParseAccount = user.uid == uiState.parseUid,
                            onClick = { viewModel.switchUser(user) },
                            onToggleParse = { viewModel.toggleParseAccount(user.uid) },
                            onExport = {
                                exportUid = user.uid
                                exportLauncher.launch("newbv-auth-${user.uid}.json")
                            },
                            onDelete = { viewModel.deleteUser(user) },
                            modifier = Modifier.focusSaverItem(focusSaver, "user_${user.uid}"),
                        )
                    }
                    item {
                        AddUserButton(
                            onClick = onNavigateLogin,
                            modifier = Modifier.focusSaverItem(focusSaver, "add_user"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 用户列表项。
 *
 * 头像 + 用户名 + 当前用户/解析账号标识 + 解析账号切换 + 导出凭证 + 删除按钮。
 *
 * @param user 用户数据。
 * @param isCurrentUser 是否为当前登录用户。
 * @param isParseAccount 是否为解析账号。
 * @param onClick 点击切换用户回调。
 * @param onToggleParse 设置/取消解析账号回调。
 * @param onExport 导出凭证回调。
 * @param onDelete 删除用户回调。
 */
@Composable
private fun UserListItem(
    user: UserEntity,
    isCurrentUser: Boolean,
    isParseAccount: Boolean,
    onClick: () -> Unit,
    onToggleParse: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            Modifier
                .width(640.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .then(modifier)
                    .touchClickable(onClick = onClick),
            onClick = onClick,
            colors =
                ControlFocusDefaults.surfaceColors(
                    containerColor =
                        if (isCurrentUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    contentColor =
                        if (isCurrentUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                ),
            border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (user.avatar.isNotEmpty()) {
                    AsyncImage(
                        model = user.avatar,
                        contentDescription = user.username,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = user.username.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Column {
                    Text(
                        text = user.username.ifEmpty { "UID: ${user.uid}" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrentUser) {
                        Text(
                            text = stringResource(R.string.user_switch_current),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (isParseAccount) {
                        Text(
                            text = stringResource(R.string.user_switch_parse_active),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onToggleParse,
            modifier = Modifier.touchClickable(onClick = onToggleParse),
            shape = IconButtonDefaults.shape(shape = ControlFocusDefaults.shape),
            scale = IconButtonDefaults.scale(focusedScale = 1f),
            colors =
                ControlFocusDefaults.buttonColors(
                    containerColor =
                        if (isParseAccount) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    contentColor =
                        if (isParseAccount) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                ),
            border = ControlFocusDefaults.buttonBorder(),
        ) {
            Icon(
                imageVector = if (isParseAccount) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription =
                    stringResource(
                        if (isParseAccount) R.string.user_switch_parse_unset else R.string.user_switch_parse_set,
                    ),
            )
        }

        IconButton(
            onClick = onExport,
            modifier = Modifier.touchClickable(onClick = onExport),
            shape = IconButtonDefaults.shape(shape = ControlFocusDefaults.shape),
            scale = IconButtonDefaults.scale(focusedScale = 1f),
            colors =
                ControlFocusDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            border = ControlFocusDefaults.buttonBorder(),
        ) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = stringResource(R.string.user_switch_export),
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.touchClickable(onClick = onDelete),
            shape = IconButtonDefaults.shape(shape = ControlFocusDefaults.shape),
            scale = IconButtonDefaults.scale(focusedScale = 1f),
            colors =
                ControlFocusDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            border = ControlFocusDefaults.buttonBorder(),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除用户",
            )
        }
    }
}

/**
 * 添加用户按钮。
 */
@Composable
private fun AddUserButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.touchClickable(onClick = onClick),
        shape = ButtonDefaults.shape(shape = ControlFocusDefaults.shape),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ControlFocusDefaults.buttonColors(),
        border = ControlFocusDefaults.buttonBorder(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
            )
            Text(text = stringResource(R.string.user_switch_add))
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun UserListItemPreview() {
    dev.frost819.newbv.core.theme.BVTheme {
        UserListItem(
            user =
                UserEntity(
                    uid = 12345L,
                    username = "测试用户",
                    avatar = "",
                    auth = "",
                ),
            isCurrentUser = true,
            isParseAccount = false,
            onClick = {},
            onToggleParse = {},
            onExport = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddUserButtonPreview() {
    dev.frost819.newbv.core.theme.BVTheme {
        AddUserButton(onClick = {})
    }
}

// endregion
