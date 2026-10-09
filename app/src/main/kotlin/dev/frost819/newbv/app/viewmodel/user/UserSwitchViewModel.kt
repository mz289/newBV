package dev.frost819.newbv.app.viewmodel.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.data.AccountRepositoryImpl
import dev.frost819.newbv.app.data.AuthData
import dev.frost819.newbv.data.db.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 账号管理 UI 状态。
 *
 * @property loading 是否正在加载。
 * @property users 用户列表。
 * @property currentUid 当前登录用户 UID（0 表示未登录）。
 * @property parseUid 双账号解析账号 UID（0 表示未启用）。
 */
data class UserSwitchUiState(
    val loading: Boolean = true,
    val users: List<UserEntity> = emptyList(),
    val currentUid: Long = 0L,
    val parseUid: Long = 0L,
)

/**
 * 账号管理 ViewModel。
 *
 * 负责加载已登录的账户列表、切换账户、删除账户、设置双账号解析账号、导出账号凭证。
 *
 * @property accountRepository 账户仓库。
 */
@HiltViewModel
class UserSwitchViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepositoryImpl,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(UserSwitchUiState())
        val uiState: StateFlow<UserSwitchUiState> = _uiState.asStateFlow()

        init {
            updateData()
        }

        /**
         * 刷新账户列表。
         */
        fun updateData() {
            viewModelScope.launch {
                val users = accountRepository.getAllUsers()
                _uiState.update {
                    it.copy(
                        loading = false,
                        users = users,
                        currentUid = accountRepository.currentUid(),
                        parseUid = accountRepository.parseAccountUid(),
                    )
                }
            }
        }

        /**
         * 切换到指定用户。
         *
         * @param user 目标用户。
         */
        fun switchUser(user: UserEntity) {
            viewModelScope.launch {
                accountRepository.setCurrentUser(user)
                updateData()
            }
        }

        /**
         * 设置/取消双账号解析账号。
         *
         * 指定账号（通常为大会员）仅用于播放地址解析；再次点击同一账号取消。
         *
         * @param uid 目标账号 UID。
         */
        fun toggleParseAccount(uid: Long) {
            viewModelScope.launch {
                val newUid = if (accountRepository.parseAccountUid() == uid) 0L else uid
                accountRepository.setParseAccount(newUid)
                updateData()
            }
        }

        /**
         * 获取指定账号的凭证 JSON（用于导出）。
         *
         * @param uid 账号 UID。
         * @return 凭证 JSON 字符串，用户不存在或凭证无效时为 null。
         */
        suspend fun exportAuthJson(uid: Long): String? =
            accountRepository.findUserByUid(uid)?.let { user ->
                runCatching { AuthData.fromJson(user.auth).toJson() }.getOrNull()
            }

        /**
         * 删除指定用户。
         *
         * 如果删除的是当前用户，且有其他账户，自动切换到第一个；
         * 若无其他账户，则执行退出登录。
         * 若删除的是解析账号，仓库内部自动取消解析设置。
         *
         * @param user 待删除的用户。
         */
        fun deleteUser(user: UserEntity) {
            viewModelScope.launch {
                val wasCurrentUser = accountRepository.currentUid() == user.uid
                accountRepository.deleteUser(user)
                val remaining = accountRepository.getAllUsers()
                if (wasCurrentUser && remaining.isNotEmpty()) {
                    accountRepository.setCurrentUser(remaining.first())
                } else if (wasCurrentUser) {
                    accountRepository.logout()
                }
                updateData()
            }
        }
    }
