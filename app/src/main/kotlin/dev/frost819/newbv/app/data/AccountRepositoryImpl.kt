package dev.frost819.newbv.app.data

import dev.frost819.newbv.app.viewmodel.common.LOAD_TIMEOUT_MS
import dev.frost819.newbv.app.viewmodel.common.rethrowUnlessTimeout
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.entity.user.MyInfoData
import dev.frost819.newbv.biliapi.repositories.AuthRepository
import dev.frost819.newbv.biliapi.repositories.ChannelRepository
import dev.frost819.newbv.biliapi.repositories.ParseAccountRepository
import dev.frost819.newbv.data.datastore.Prefs
import dev.frost819.newbv.data.db.dao.UserDao
import dev.frost819.newbv.data.db.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AccountRepository] 的实现。
 *
 * 聚合三层数据源：
 * - [UserDao]（Room）：多账户持久化
 * - [Prefs]（DataStore）：当前登录用户凭证
 * - [AuthRepository]（内存）：bili-api 层鉴权状态
 *
 * 通过 [uiState] 暴露当前登录状态给 UI 层观察。
 *
 * @property userDao Room DAO。
 * @property authRepository bili-api 鉴权状态。
 */
@Singleton
class AccountRepositoryImpl
    @Inject
    constructor(
        private val userDao: UserDao,
        private val authRepository: AuthRepository,
        private val channelRepository: ChannelRepository,
        private val parseAccountRepository: ParseAccountRepository,
    ) {
        /** UI 状态：当前登录用户信息。 */
        private val _uiState = MutableStateFlow(AccountUiState())
        val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()
        private var accountGeneration = 0L

        init {
            initFromPrefs()
        }

        /**
         * 从 Prefs 初始化登录态。
         *
         * 在 Application 启动后首次构造时调用，
         * 将 Prefs 中已有的凭证同步到 [AuthRepository] 和 [uiState]。
         */
        private fun initFromPrefs() {
            if (Prefs.isLogin) {
                syncToAuthRepository(
                    uid = Prefs.uid,
                    sessData = Prefs.sessData,
                    biliJct = Prefs.biliJct,
                    accessToken = Prefs.accessToken,
                )
                _uiState.update {
                    it.copy(
                        isLogin = true,
                        uid = Prefs.uid,
                        username = "",
                        avatar = "",
                    )
                }
            }
        }

        /**
         * 将凭证同步到 [AuthRepository]。
         *
         * bili-api 层的 Repository 通过 [AuthRepository] 读取当前凭证，
         * 此方法在用户切换或登录后调用。
         *
         * @param uid 用户 UID。
         * @param sessData SESSDATA Cookie。
         * @param biliJct bili_jct CSRF token。
         * @param accessToken App 接口 token。
         */
        private fun syncToAuthRepository(
            uid: Long,
            sessData: String,
            biliJct: String,
            accessToken: String,
        ) {
            authRepository.mid = uid
            authRepository.sessionData = sessData
            authRepository.biliJct = biliJct
            authRepository.accessToken = accessToken
            authRepository.buvid3 = Prefs.buvid3
            authRepository.buvid = Prefs.buvid

            BiliHttpApi.sessData = sessData
            BiliHttpApi.biliJct = biliJct
            BiliHttpApi.mid = uid
            BiliHttpApi.accessToken = accessToken
            if (accessToken.isNotBlank() && Prefs.buvid.isNotBlank()) {
                channelRepository.initDefaultChannel(accessToken, Prefs.buvid)
            } else {
                channelRepository.close()
            }
        }

        suspend fun getAllUsers(): List<UserEntity> = userDao.getAll()

        suspend fun findUserByUid(uid: Long): UserEntity? = userDao.findUserByUid(uid)

        /** 当前解析账号 UID（0 = 关闭）。 */
        fun parseAccountUid(): Long = Prefs.parseAccountUid

        /**
         * 设置双账号解析账号。
         *
         * 写入 Prefs 并同步凭证到 [ParseAccountRepository]（bili-api 层播放解析读取）。
         *
         * @param uid 解析账号 UID，0 表示关闭。
         */
        suspend fun setParseAccount(uid: Long) {
            Prefs.parseAccountUid = uid
            syncParseAccount(uid)
        }

        /**
         * 从 Prefs 同步解析账号凭证到 [ParseAccountRepository]。
         *
         * 在应用启动时调用（播放解析可能早于任何账号操作）。
         */
        suspend fun syncParseAccountFromPrefs() {
            syncParseAccount(Prefs.parseAccountUid)
        }

        /**
         * 加载指定 UID 的凭证并同步到 [ParseAccountRepository]。
         *
         * UID 为 0、用户不存在或凭证无效（SESSDATA 为空）时清空解析账号。
         */
        private suspend fun syncParseAccount(uid: Long) {
            if (uid == 0L) {
                parseAccountRepository.clear()
                return
            }
            val authData =
                userDao.findUserByUid(uid)?.let { user ->
                    runCatching { AuthData.fromJson(user.auth) }.getOrNull()
                }
            if (authData != null) {
                parseAccountRepository.update(authData.uid, authData.sessData, authData.biliJct)
            } else {
                parseAccountRepository.clear()
                Prefs.parseAccountUid = 0L
            }
        }

        suspend fun upsertUser(user: UserEntity) {
            val existing = userDao.findUserByUid(user.uid)
            if (existing != null) {
                existing.auth = user.auth
                userDao.update(existing)
            } else {
                userDao.insert(user)
            }
        }

        suspend fun deleteUser(user: UserEntity) {
            userDao.delete(user)
            if (Prefs.parseAccountUid == user.uid) {
                setParseAccount(0L)
            }
        }

        fun isLogin(): Boolean = Prefs.isLogin

        fun currentUid(): Long = Prefs.uid

        /**
         * 设置当前登录用户。
         *
         * 执行步骤：
         * 1. 从 [UserEntity.auth] 反序列化 [AuthData]。
         * 2. 写入 Prefs（使 bili-api 层可读取）。
         * 3. 同步到 [AuthRepository]。
         * 4. 更新 [uiState]。
         *
         * @param user 登录成功的用户。
         */
        suspend fun setCurrentUser(user: UserEntity) {
            val authData = AuthData.fromJson(user.auth)
            accountGeneration++
            authData.saveToPrefs()
            syncToAuthRepository(
                uid = authData.uid,
                sessData = authData.sessData,
                biliJct = authData.biliJct,
                accessToken = authData.accessToken,
            )
            _uiState.update {
                it.copy(
                    isLogin = true,
                    uid = user.uid,
                    username = user.username,
                    avatar = user.avatar,
                    level = 0,
                    currentMin = 0,
                    exp = 0,
                    nextExp = 0,
                )
            }
            refreshUserInfo()
        }

        /**
         * 添加新登录用户。
         *
         * 若 UID 已存在则更新凭证，否则插入新记录。设置为当前用户。
         *
         * @param authData 登录返回的凭证。
         */
        suspend fun addUser(authData: AuthData) {
            val user =
                UserEntity(
                    uid = authData.uid,
                    username = "",
                    avatar = "",
                    auth = authData.toJson(),
                )
            upsertUser(user)
            accountGeneration++
            authData.saveToPrefs()
            syncToAuthRepository(
                uid = authData.uid,
                sessData = authData.sessData,
                biliJct = authData.biliJct,
                accessToken = authData.accessToken,
            )
            _uiState.update {
                it.copy(
                    isLogin = true,
                    uid = authData.uid,
                    username = "",
                    avatar = "",
                    level = 0,
                    currentMin = 0,
                    exp = 0,
                    nextExp = 0,
                )
            }
            refreshUserInfo()
        }

        /**
         * 从网络刷新当前用户信息（用户名、头像）。
         *
         * 调用 BiliHttpApi 获取用户自身信息，更新 Room 和 [uiState]。
         */
        suspend fun refreshUserInfo() {
            if (!Prefs.isLogin) return
            val uid = Prefs.uid
            val generation = accountGeneration
            runCatching {
                val response: MyInfoData =
                    withTimeout(LOAD_TIMEOUT_MS) { BiliHttpApi.getUserSelfInfo().getResponseData() }
                if (generation != accountGeneration || !Prefs.isLogin || Prefs.uid != uid) return
                val user = userDao.findUserByUid(uid) ?: return
                if (generation != accountGeneration) return
                user.username = response.name
                user.avatar = response.face
                userDao.update(user)
                _uiState.update {
                    if (generation != accountGeneration || !Prefs.isLogin || Prefs.uid != uid) {
                        it
                    } else {
                        it.copy(
                            username = response.name,
                            avatar = response.face,
                            level = response.levelExp.currentLevel,
                            currentMin = response.levelExp.currentMin,
                            exp = response.levelExp.currentExp,
                            nextExp = response.levelExp.nextExp,
                        )
                    }
                }
            }.onFailure { it.rethrowUnlessTimeout() }
        }

        /**
         * 从 Room 加载当前用户的用户名和头像（不请求网络）。
         */
        suspend fun reloadAvatar() {
            if (!Prefs.isLogin) return
            val uid = Prefs.uid
            val generation = accountGeneration
            val user = userDao.findUserByUid(uid) ?: return
            _uiState.update {
                if (generation != accountGeneration || !Prefs.isLogin || Prefs.uid != uid) {
                    it
                } else {
                    it.copy(
                        username = user.username,
                        avatar = user.avatar,
                    )
                }
            }
        }

        suspend fun logout() {
            accountGeneration++
            val user = userDao.findUserByUid(Prefs.uid)
            if (user != null) {
                userDao.delete(user)
            }
            Prefs.apply {
                isLogin = false
                uid = 0L
                sid = ""
                sessData = ""
                biliJct = ""
                uidCkMd5 = ""
                accessToken = ""
                refreshToken = ""
                tokenExpiredDate = java.util.Date(0)
            }
            authRepository.apply {
                mid = null
                sessionData = null
                biliJct = null
                accessToken = null
            }
            BiliHttpApi.apply {
                mid = null
                sessData = ""
                biliJct = ""
                accessToken = ""
            }
            channelRepository.close()
            _uiState.value = AccountUiState()
        }

        /**
         * 切换无痕模式。
         *
         * 状态只存于 [Prefs]（UI 经 [Prefs.incognitoModeFlow] 观察），不进入 [AccountUiState]。
         */
        fun toggleIncognitoMode() {
            Prefs.incognitoMode = !Prefs.incognitoMode
        }
    }

/**
 * 账户 UI 状态。
 *
 * @property isLogin 是否已登录。
 * @property uid 当前用户 UID。
 * @property username 用户名。
 * @property avatar 头像 URL。
 * @property level 用户等级。
 * @property currentMin 当前等级经验最低值。
 * @property exp 当前经验值（总经验）。
 * @property nextExp 下一等级所需经验值（门槛，非剩余）。Lv6 满级时为 0。
 */
data class AccountUiState(
    val isLogin: Boolean = false,
    val uid: Long = 0L,
    val username: String = "",
    val avatar: String = "",
    val level: Int = 0,
    val currentMin: Int = 0,
    val exp: Int = 0,
    val nextExp: Int = 0,
)
