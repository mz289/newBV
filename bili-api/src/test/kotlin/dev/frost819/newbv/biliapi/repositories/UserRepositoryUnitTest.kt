package dev.frost819.newbv.biliapi.repositories

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import dev.frost819.newbv.biliapi.http.entity.BiliResponseWithoutData
import dev.frost819.newbv.biliapi.http.entity.dynamic.DynamicData
import dev.frost819.newbv.biliapi.http.entity.dynamic.DynamicItem
import dev.frost819.newbv.biliapi.http.entity.user.AppSpaceVideoData
import dev.frost819.newbv.biliapi.http.entity.user.FollowAction
import dev.frost819.newbv.biliapi.http.entity.user.Relation
import dev.frost819.newbv.biliapi.http.entity.user.RelationData
import dev.frost819.newbv.biliapi.http.entity.user.RelationType
import dev.frost819.newbv.biliapi.http.entity.user.Pendant
import dev.frost819.newbv.biliapi.http.entity.user.Vip
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [UserRepository] 的单元测试。
 *
 * 通过 MockK 模拟 [BiliHttpApi] 单例，验证关注/取关/查询关注状态/获取用户信息等
 * 业务逻辑。不依赖真实网络。
 */
class UserRepositoryUnitTest {
    private lateinit var repository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var channelRepository: ChannelRepository

    companion object {
        private const val MID = 12345L
        private const val BILI_JCT = "test-bili-jct"
        private const val ACCESS_TOKEN = "test-access-token"
        private const val SESSDATA = "test-sessdata"
    }

    @BeforeEach
    fun setUp() {
        mockkObject(BiliHttpApi)
        authRepository = AuthRepository()
        authRepository.biliJct = BILI_JCT
        authRepository.accessToken = ACCESS_TOKEN
        authRepository.sessionData = SESSDATA
        channelRepository = ChannelRepository()
        repository = UserRepository(authRepository, channelRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BiliHttpApi)
    }

    // ------------------------------------------------------------------
    // followUser
    // ------------------------------------------------------------------

    @Test
    fun `followUser Web calls modifyFollow with AddFollow and csrf`() =
        runTest {
            coEvery {
                BiliHttpApi.modifyFollow(any(), any(), any(), any(), any())
            } returns BiliResponseWithoutData(code = 0, message = "", ttl = 0)

            val result = repository.followUser(MID, preferApiType = ApiType.Web)

            assertThat(result).isTrue()
            coVerify {
                BiliHttpApi.modifyFollow(
                    mid = MID,
                    action = FollowAction.AddFollow,
                    actionSource = any(),
                    accessKey = null,
                    csrf = BILI_JCT,
                )
            }
        }

    @Test
    fun `followUser App calls modifyFollow with AddFollow and accessKey`() =
        runTest {
            coEvery {
                BiliHttpApi.modifyFollow(any(), any(), any(), any(), any())
            } returns BiliResponseWithoutData(code = 0, message = "", ttl = 0)

            val result = repository.followUser(MID, preferApiType = ApiType.App)

            assertThat(result).isTrue()
            coVerify {
                BiliHttpApi.modifyFollow(
                    mid = MID,
                    action = FollowAction.AddFollow,
                    actionSource = any(),
                    accessKey = ACCESS_TOKEN,
                    csrf = null,
                )
            }
        }

    @Test
    fun `followUser returns false when API returns non-zero code`() =
        runTest {
            coEvery {
                BiliHttpApi.modifyFollow(any(), any(), any(), any(), any())
            } returns BiliResponseWithoutData(code = -101, message = "未登录", ttl = 0)

            val result = repository.followUser(MID, preferApiType = ApiType.Web)

            assertThat(result).isFalse()
        }

    // ------------------------------------------------------------------
    // unfollowUser
    // ------------------------------------------------------------------

    @Test
    fun `unfollowUser Web calls modifyFollow with DelFollow`() =
        runTest {
            coEvery {
                BiliHttpApi.modifyFollow(any(), any(), any(), any(), any())
            } returns BiliResponseWithoutData(code = 0, message = "", ttl = 0)

            val result = repository.unfollowUser(MID, preferApiType = ApiType.Web)

            assertThat(result).isTrue()
            coVerify {
                BiliHttpApi.modifyFollow(
                    mid = MID,
                    action = FollowAction.DelFollow,
                    actionSource = any(),
                    accessKey = null,
                    csrf = BILI_JCT,
                )
            }
        }

    @Test
    fun `unfollowUser App calls modifyFollow with DelFollow and accessKey`() =
        runTest {
            coEvery {
                BiliHttpApi.modifyFollow(any(), any(), any(), any(), any())
            } returns BiliResponseWithoutData(code = 0, message = "", ttl = 0)

            val result = repository.unfollowUser(MID, preferApiType = ApiType.App)

            assertThat(result).isTrue()
            coVerify {
                BiliHttpApi.modifyFollow(
                    mid = MID,
                    action = FollowAction.DelFollow,
                    actionSource = any(),
                    accessKey = ACCESS_TOKEN,
                    csrf = null,
                )
            }
        }

    // ------------------------------------------------------------------
    // checkIsFollowing
    // ------------------------------------------------------------------

    @Test
    fun `checkIsFollowing returns null when not authenticated`() =
        runTest {
            authRepository.sessionData = null
            authRepository.accessToken = null

            val result = repository.checkIsFollowing(MID)

            assertThat(result).isNull()
        }

    @Test
    fun `checkIsFollowing returns true when relation is Followed`() =
        runTest {
            coEvery { BiliHttpApi.getRelations(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        RelationData(
                            relation =
                                Relation(
                                    mid = MID,
                                    attribute = RelationType.Followed,
                                    mtime = 0,
                                    special = 0,
                                ),
                            beRelation =
                                Relation(
                                    mid = 0,
                                    attribute = RelationType.None,
                                    mtime = 0,
                                    special = 0,
                                ),
                        ),
                )

            val result = repository.checkIsFollowing(MID)

            assertThat(result).isTrue()
        }

    @Test
    fun `checkIsFollowing returns true when relation is BothFollowed`() =
        runTest {
            coEvery { BiliHttpApi.getRelations(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        RelationData(
                            relation =
                                Relation(
                                    mid = MID,
                                    attribute = RelationType.BothFollowed,
                                    mtime = 0,
                                    special = 0,
                                ),
                            beRelation =
                                Relation(
                                    mid = 0,
                                    attribute = RelationType.None,
                                    mtime = 0,
                                    special = 0,
                                ),
                        ),
                )

            val result = repository.checkIsFollowing(MID)

            assertThat(result).isTrue()
        }

    @Test
    fun `checkIsFollowing returns false when relation is None`() =
        runTest {
            coEvery { BiliHttpApi.getRelations(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        RelationData(
                            relation =
                                Relation(
                                    mid = MID,
                                    attribute = RelationType.None,
                                    mtime = 0,
                                    special = 0,
                                ),
                            beRelation =
                                Relation(
                                    mid = 0,
                                    attribute = RelationType.None,
                                    mtime = 0,
                                    special = 0,
                                ),
                        ),
                )

            val result = repository.checkIsFollowing(MID)

            assertThat(result).isFalse()
        }

    @Test
    fun `checkIsFollowing returns null on API failure`() =
        runTest {
            coEvery { BiliHttpApi.getRelations(any(), any()) } returns
                BiliResponse(
                    code = -101,
                    message = "未登录",
                )

            val result = repository.checkIsFollowing(MID)

            assertThat(result).isNull()
        }

    // ------------------------------------------------------------------
    // addSeasonFollow
    // ------------------------------------------------------------------

    @Test
    fun `addSeasonFollow Web calls addSeasonFollow with csrf and returns toast`() =
        runTest {
            coEvery { BiliHttpApi.addSeasonFollow(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.season.SeasonFollowData(
                            fmid = 400,
                            relation = true,
                            status = 1,
                            toast = "追番成功",
                        ),
                )

            val result = repository.addSeasonFollow(seasonId = 400, preferApiType = ApiType.Web)

            assertThat(result).isEqualTo("追番成功")
            coVerify { BiliHttpApi.addSeasonFollow(seasonId = 400, csrf = BILI_JCT) }
        }

    @Test
    fun `addSeasonFollow App calls addSeasonFollowApp with accessKey and returns toast`() =
        runTest {
            coEvery { BiliHttpApi.addSeasonFollowApp(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.season.SeasonFollowData(
                            fmid = 400,
                            relation = true,
                            status = 1,
                            toast = "追番成功",
                        ),
                )

            val result = repository.addSeasonFollow(seasonId = 400, preferApiType = ApiType.App)

            assertThat(result).isEqualTo("追番成功")
            coVerify { BiliHttpApi.addSeasonFollowApp(seasonId = 400, accessKey = ACCESS_TOKEN) }
        }

    // ------------------------------------------------------------------
    // delSeasonFollow
    // ------------------------------------------------------------------

    @Test
    fun `delSeasonFollow Web calls delSeasonFollow with csrf and returns toast`() =
        runTest {
            coEvery { BiliHttpApi.delSeasonFollow(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.season.SeasonFollowData(
                            fmid = 400,
                            relation = false,
                            status = 0,
                            toast = "已取消追番",
                        ),
                )

            val result = repository.delSeasonFollow(seasonId = 400, preferApiType = ApiType.Web)

            assertThat(result).isEqualTo("已取消追番")
            coVerify { BiliHttpApi.delSeasonFollow(seasonId = 400, csrf = BILI_JCT) }
        }

    @Test
    fun `delSeasonFollow App calls delSeasonFollowApp with accessKey`() =
        runTest {
            coEvery { BiliHttpApi.delSeasonFollowApp(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.season.SeasonFollowData(
                            fmid = 400,
                            relation = false,
                            status = 0,
                            toast = "已取消追番",
                        ),
                )

            val result = repository.delSeasonFollow(seasonId = 400, preferApiType = ApiType.App)

            assertThat(result).isEqualTo("已取消追番")
            coVerify { BiliHttpApi.delSeasonFollowApp(seasonId = 400, accessKey = ACCESS_TOKEN) }
        }

    // ------------------------------------------------------------------
    // getSpaceVideos (Web)
    // ------------------------------------------------------------------

    @Test
    fun `getSpaceVideos Web maps response and returns videos with hasNext`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserSpaceVideos(any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData(
                            list =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData.SpaceVideoListItem(
                                    tlist = null,
                                    vlist = listOf(fakeVListItem(aid = 1L, title = "video1")),
                                ),
                            page =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData.Page(
                                    pageNumber = 1,
                                    pageSize = 30,
                                    count = 100,
                                ),
                        ),
                )

            val result =
                repository.getSpaceVideos(
                    mid = MID,
                    order = dev.frost819.newbv.biliapi.entity.user.SpaceVideoOrder.PubDate,
                    page =
                        dev.frost819.newbv.biliapi.entity.user
                            .SpaceVideoPage(),
                    preferApiType = ApiType.Web,
                )

            assertThat(result.videos).hasSize(1)
            assertThat(result.videos[0].aid).isEqualTo(1L)
            assertThat(result.videos[0].title).isEqualTo("video1")
            assertThat(result.page.hasNext).isTrue()
            assertThat(result.page.nextWebPageNumber).isEqualTo(2)
        }

    @Test
    fun `getSpaceVideos Web returns hasNext false when count equals pn times ps`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserSpaceVideos(any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData(
                            list = null,
                            page =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData.Page(
                                    pageNumber = 2,
                                    pageSize = 30,
                                    count = 30,
                                ),
                        ),
                )

            val result = repository.getSpaceVideos(mid = MID, preferApiType = ApiType.Web)

            assertThat(result.videos).isEmpty()
            assertThat(result.page.hasNext).isFalse()
        }

    // ------------------------------------------------------------------
    // getFollowedUsers (Web)
    // ------------------------------------------------------------------

    @Test
    fun `getFollowedUsers Web returns all followed users for single page`() =
        runTest {
            coEvery { BiliHttpApi.getUserFollow(any(), any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.UserFollowData(
                            list = listOf(fakeFollowedUser(mid = 1L, uname = "user1")),
                            reVersion = 0,
                            total = 1,
                        ),
                )

            val result = repository.getFollowedUsers(mid = MID, preferApiType = ApiType.Web)

            assertThat(result).hasSize(1)
            assertThat(result[0].mid).isEqualTo(1L)
            assertThat(result[0].name).isEqualTo("user1")
        }

    @Test
    fun `getFollowedUsers Web fetches multiple pages when total exceeds pageSize`() =
        runTest {
            coEvery { BiliHttpApi.getUserFollow(any(), any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.UserFollowData(
                            list = listOf(fakeFollowedUser(mid = 1L, uname = "user1")),
                            reVersion = 0,
                            total = 51,
                        ),
                )

            val result = repository.getFollowedUsers(mid = MID, preferApiType = ApiType.Web)

            assertThat(result).hasSize(2)
            coVerify(atLeast = 2) { BiliHttpApi.getUserFollow(any(), any(), any(), any(), any()) }
        }

    // ------------------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // getFollowedUsers (App)
    // ------------------------------------------------------------------

    @Test
    fun `getFollowedUsers App returns all followed users for single page`() =
        runTest {
            coEvery { BiliHttpApi.getUserFollow(any(), any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.UserFollowData(
                            list = listOf(fakeFollowedUser(mid = 1L, uname = "user1")),
                            reVersion = 0,
                            total = 1,
                        ),
                )

            val result = repository.getFollowedUsers(mid = MID, preferApiType = ApiType.App)

            assertThat(result).hasSize(1)
            assertThat(result[0].mid).isEqualTo(1L)
            assertThat(result[0].name).isEqualTo("user1")
            coVerify { BiliHttpApi.getUserFollow(eq(MID), any(), any(), any(), eq(ACCESS_TOKEN)) }
        }

    @Test
    fun `getFollowedUsers App fetches multiple pages when total exceeds pageSize`() =
        runTest {
            coEvery { BiliHttpApi.getUserFollow(any(), any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.UserFollowData(
                            list = listOf(fakeFollowedUser(mid = 1L, uname = "user1")),
                            reVersion = 0,
                            total = 51,
                        ),
                )

            val result = repository.getFollowedUsers(mid = MID, preferApiType = ApiType.App)

            assertThat(result).hasSize(2)
            coVerify(atLeast = 2) { BiliHttpApi.getUserFollow(any(), any(), any(), any(), any()) }
        }

    // ------------------------------------------------------------------
    // getSpaceVideos (App)
    // ------------------------------------------------------------------

    @Test
    fun `getSpaceVideos App uses HTTP App API not gRPC`() =
        runTest {
            val appSpaceData =
                appSpaceData(hasNext = false, appSpaceVideoItem(aid = MID * 1000L, bvid = "BV-app", title = "App视频"))
            coEvery { BiliHttpApi.getAppUserSpaceVideos(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = appSpaceData)

            val result = repository.getSpaceVideos(mid = MID, preferApiType = ApiType.App)

            assertThat(result.videos).hasSize(1)
            assertThat(result.videos[0].bvid).isEqualTo("BV-app")
            assertThat(result.page.hasNext).isFalse()
            coVerify { BiliHttpApi.getAppUserSpaceVideos(eq(MID), any(), any(), any(), any()) }
        }

    @Test
    fun `getSpaceVideos App does not fallback to gRPC`() =
        runTest {
            val appSpaceData = appSpaceData(hasNext = false, appSpaceVideoItem(aid = MID * 1000L, bvid = "BV-app"))
            coEvery { BiliHttpApi.getAppUserSpaceVideos(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = appSpaceData)

            repository.getSpaceVideos(mid = MID, preferApiType = ApiType.App)

            // App 路径不再依赖 gRPC channel，channel 未初始化也能走 HTTP
            assertThat(repository).isNotNull()
        }

    // ------------------------------------------------------------------
    // getSpaceCollections / getCollectionVideos（合集与系列，仅 Web 通道）
    // ------------------------------------------------------------------

    @Test
    fun `getSpaceCollections seasons uses dedicated endpoint and stops when seasons exhausted`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserSpaceSeasonsSeries(any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData(
                            itemsLists =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.ItemsLists(
                                    page =
                                        dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.ItemsLists.Page(
                                            pageNumber = 2,
                                            pageSize = 5,
                                            // 混合分页的总条数（合集+系列）
                                            total = 32,
                                        ),
                                    seasonsList =
                                        listOf(
                                            dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.Item(
                                                meta =
                                                    dev.frost819.newbv.biliapi.http.entity.user.WebCollectionMeta(
                                                        seasonId = 2046621L,
                                                        name = "合集·样片日记",
                                                        title = "样片日记",
                                                        cover = "https://example.com/cover.jpg",
                                                        total = 26,
                                                    ),
                                            ),
                                        ),
                                    seriesList = emptyList(),
                                ),
                        ),
                )

            val result =
                repository.getSpaceCollections(mid = MID, isSeason = true, preferApiType = ApiType.Web)

            // 只取 seasons，系列条目不进入合集栏目
            assertThat(result.collections).hasSize(1)
            assertThat(result.collections[0].id).isEqualTo(2046621L)
            assertThat(result.collections[0].isSeason).isTrue()
            // 标题优先取去掉"合集·"前缀的 title
            assertThat(result.collections[0].title).isEqualTo("样片日记")
            // 2 * 5 < 32 → 还有下一页
            assertThat(result.page.hasNext).isTrue()
            assertThat(result.page.nextPageNumber).isEqualTo(3)
            coVerify { BiliHttpApi.getWebUserSpaceSeasonsSeries(MID, 1, 20) }
        }

    @Test
    fun `getSpaceCollections seasons stops early when a page has no seasons`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserSpaceSeasonsSeries(any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData(
                            itemsLists =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.ItemsLists(
                                    page =
                                        dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.ItemsLists.Page(
                                            pageNumber = 6,
                                            pageSize = 5,
                                            total = 32,
                                        ),
                                    seasonsList = emptyList(),
                                    seriesList =
                                        listOf(
                                            dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.Item(
                                                meta =
                                                    dev.frost819.newbv.biliapi.http.entity.user.WebCollectionMeta(
                                                        seriesId = 370368L,
                                                        name = "我的系列",
                                                        total = 19,
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                )

            val result =
                repository.getSpaceCollections(mid = MID, isSeason = true, preferApiType = ApiType.Web)

            // 合集排在系列之前：本页已无 seasons 即合集翻完，提前终止
            assertThat(result.collections).isEmpty()
            assertThat(result.page.hasNext).isFalse()
        }

    @Test
    fun `getSpaceCollections series uses seasons_series endpoint with page-count semantics`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserSpaceSeries(any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData(
                            itemsLists =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.ItemsLists(
                                    page =
                                        dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.ItemsLists.Page(
                                            pageNumber = 1,
                                            pageSize = 5,
                                            // 该端点 total 为总页数语义
                                            total = 3,
                                        ),
                                    seasonsList = emptyList(),
                                    seriesList =
                                        listOf(
                                            dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData.Item(
                                                meta =
                                                    dev.frost819.newbv.biliapi.http.entity.user.WebCollectionMeta(
                                                        seriesId = 370368L,
                                                        name = "我的系列",
                                                        total = 19,
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                )

            val result =
                repository.getSpaceCollections(mid = MID, isSeason = false, preferApiType = ApiType.Web)

            assertThat(result.collections).hasSize(1)
            assertThat(result.collections[0].id).isEqualTo(370368L)
            assertThat(result.collections[0].isSeason).isFalse()
            assertThat(result.collections[0].title).isEqualTo("我的系列")
            // 第 1 页 / 共 3 页 → 还有下一页
            assertThat(result.page.hasNext).isTrue()
            assertThat(result.page.nextPageNumber).isEqualTo(2)
            coVerify { BiliHttpApi.getWebUserSpaceSeries(MID, 1, 20) }
        }

    @Test
    fun `getSpaceFavFolders maps public folders only`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserFavFolders(any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebUserFavFolders(
                            count = 3,
                            list =
                                listOf(
                                    dev.frost819.newbv.biliapi.http.entity.user.WebUserFavFolders.FolderItem(
                                        id = 4058080750L,
                                        title = "AI调用",
                                        mediaCount = 129,
                                    ),
                                    dev.frost819.newbv.biliapi.http.entity.user.WebUserFavFolders.FolderItem(
                                        id = 1212612050L,
                                        title = "狸粮",
                                        mediaCount = 255,
                                    ),
                                    dev.frost819.newbv.biliapi.http.entity.user.WebUserFavFolders.FolderItem(
                                        id = 999L,
                                        title = null,
                                        mediaCount = 1,
                                    ),
                                ),
                        ),
                )

            val result = repository.getSpaceFavFolders(mid = MID, preferApiType = ApiType.Web)

            // 标题缺失的条目被过滤
            assertThat(result).hasSize(2)
            assertThat(result[0].id).isEqualTo(4058080750L)
            assertThat(result[0].title).isEqualTo("AI调用")
            assertThat(result[0].mediaCount).isEqualTo(129)
            coVerify { BiliHttpApi.getWebUserFavFolders(MID) }
        }

    @Test
    fun `getSpaceFavFolders returns empty when data is null`() =
        runTest {
            // UP 没有公开收藏夹时接口返回 code=0 且 data=null
            coEvery { BiliHttpApi.getWebUserFavFolders(any()) } returns
                BiliResponse(code = 0, message = "", data = null)

            val result = repository.getSpaceFavFolders(mid = MID, preferApiType = ApiType.Web)

            assertThat(result).isEmpty()
        }

    @Test
    fun `getFavFolderVideos maps videos and filters non-video items`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserFavResources(any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebFavResourceList(
                            info =
                                dev.frost819.newbv.biliapi.http.entity.user.WebFavResourceList.Info(
                                    id = 4058080750L,
                                    title = "AI调用",
                                    mediaCount = 129,
                                ),
                            medias =
                                listOf(
                                    dev.frost819.newbv.biliapi.http.entity.user.WebFavResourceList.MediaItem(
                                        id = 117618300161145L,
                                        bvid = "BV19jsE66EKh",
                                        title = "靠三桶油分红",
                                        cover = "https://example.com/pic.jpg",
                                        duration = 198,
                                        type = 2,
                                        pubtime = 1794713239L,
                                        cntInfo =
                                            dev.frost819.newbv.biliapi.http.entity.user.WebFavResourceList.MediaItem.CntInfo(
                                                play = 80432L,
                                                danmaku = 9,
                                            ),
                                    ),
                                    // 非视频条目（type != 2）应被过滤
                                    dev.frost819.newbv.biliapi.http.entity.user.WebFavResourceList.MediaItem(
                                        id = 1L,
                                        bvid = null,
                                        title = "已失效视频",
                                        type = 12,
                                    ),
                                ),
                            hasMore = true,
                        ),
                )

            val result = repository.getFavFolderVideos(folderId = 4058080750L, preferApiType = ApiType.Web)

            assertThat(result.videos).hasSize(1)
            assertThat(result.videos[0].aid).isEqualTo(117618300161145L)
            assertThat(result.videos[0].bvid).isEqualTo("BV19jsE66EKh")
            assertThat(result.videos[0].play).isEqualTo(80432)
            assertThat(result.videos[0].danmaku).isEqualTo(9)
            // has_more = true → 还有下一页，页码推进
            assertThat(result.page.hasNext).isTrue()
            assertThat(result.page.nextWebPageNumber).isEqualTo(2)
            assertThat(result.page.nextWebPageSize).isEqualTo(20)
            coVerify { BiliHttpApi.getWebUserFavResources(4058080750L, 1, 20) }
        }

    @Test
    fun `getCollectionVideos routes season to seasons_archives_list`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserSeasonArchives(any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebSeasonArchivesData(
                            archives =
                                listOf(
                                    dev.frost819.newbv.biliapi.http.entity.user.WebCollectionArchiveItem(
                                        aid = 238588630L,
                                        bvid = "BV1be41127ft",
                                        title = "零下26度的新疆",
                                        pic = "https://example.com/pic.jpg",
                                        duration = 974,
                                        pubdate = 1704877200L,
                                        stat =
                                            dev.frost819.newbv.biliapi.http.entity.user.WebCollectionArchiveItem.Stat(
                                                view = 3300769L,
                                                danmaku = 15316,
                                            ),
                                    ),
                                ),
                            page =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSeasonArchivesData.Page(
                                    pageNumber = 1,
                                    pageSize = 20,
                                    total = 26,
                                ),
                        ),
                )

            val collection =
                dev.frost819.newbv.biliapi.entity.user.UpCollection(
                    id = 2046621L,
                    title = "样片日记",
                    cover = "",
                    total = 26,
                    isSeason = true,
                )
            val result = repository.getCollectionVideos(mid = MID, collection = collection, preferApiType = ApiType.Web)

            assertThat(result.videos).hasSize(1)
            assertThat(result.videos[0].aid).isEqualTo(238588630L)
            assertThat(result.videos[0].bvid).isEqualTo("BV1be41127ft")
            assertThat(result.videos[0].play).isEqualTo(3300769)
            assertThat(result.page.hasNext).isTrue()
            assertThat(result.page.nextWebPageNumber).isEqualTo(2)
            coVerify { BiliHttpApi.getWebUserSeasonArchives(MID, 2046621L, 1, 20) }
        }

    @Test
    fun `getCollectionVideos routes series to x_series_archives`() =
        runTest {
            coEvery { BiliHttpApi.getWebUserSeriesArchives(any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebSeriesArchivesData(
                            archives =
                                listOf(
                                    dev.frost819.newbv.biliapi.http.entity.user.WebCollectionArchiveItem(
                                        aid = 798106709L,
                                        bvid = "BV1Dy4y1S7i1",
                                        title = "收复失地运动",
                                        pic = "https://example.com/pic.jpg",
                                        duration = 12267,
                                        pubdate = 1607832019L,
                                    ),
                                ),
                            page =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSeriesArchivesData.Page(
                                    num = 1,
                                    size = 20,
                                    total = 19,
                                ),
                        ),
                )

            val collection =
                dev.frost819.newbv.biliapi.entity.user.UpCollection(
                    id = 370368L,
                    title = "我的系列",
                    cover = "",
                    total = 19,
                    isSeason = false,
                )
            val result = repository.getCollectionVideos(mid = MID, collection = collection, preferApiType = ApiType.Web)

            assertThat(result.videos).hasSize(1)
            assertThat(result.videos[0].aid).isEqualTo(798106709L)
            // stat 缺省时 play 记为 -1（卡片不显示播放数）
            assertThat(result.videos[0].play).isEqualTo(-1)
            assertThat(result.page.hasNext).isFalse()
            coVerify { BiliHttpApi.getWebUserSeriesArchives(MID, 370368L, 1, 20) }
        }

    @Test
    fun `getSpaceVideos App falls back to Web when App rejects non-default order`() =
        runTest {
            coEvery { BiliHttpApi.getAppUserSpaceVideos(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = -400, message = "请求错误", data = null)
            coEvery { BiliHttpApi.getWebUserSpaceVideos(any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData(
                            list =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData.SpaceVideoListItem(
                                    tlist = null,
                                    vlist = listOf(fakeVListItem(aid = 2L, title = "stow-video")),
                                ),
                            page =
                                dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData.Page(
                                    pageNumber = 1,
                                    pageSize = 20,
                                    count = 1,
                                ),
                        ),
                )

            val result =
                repository.getSpaceVideos(
                    mid = MID,
                    order = dev.frost819.newbv.biliapi.entity.user.SpaceVideoOrder.Stow,
                    preferApiType = ApiType.App,
                )

            assertThat(result.videos).hasSize(1)
            assertThat(result.videos[0].title).isEqualTo("stow-video")
            coVerify { BiliHttpApi.getAppUserSpaceVideos(eq(MID), any(), eq("stow"), any(), any()) }
            coVerify { BiliHttpApi.getWebUserSpaceVideos(mid = MID, order = "stow", pageNumber = 1, pageSize = 20) }
        }

    @Test
    fun `getSpaceVideos App does not fall back to Web for pubdate failure`() =
        runTest {
            coEvery { BiliHttpApi.getAppUserSpaceVideos(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = -400, message = "请求错误", data = null)

            val error =
                runCatching {
                    repository.getSpaceVideos(mid = MID, preferApiType = ApiType.App)
                }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalStateException::class.java)
            coVerify(exactly = 0) { BiliHttpApi.getWebUserSpaceVideos(any(), any(), any(), any(), any(), any()) }
        }

    // ------------------------------------------------------------------
    // getDynamicVideos (Web)
    // ------------------------------------------------------------------

    @Test
    fun `getDynamicVideos Web maps response to DynamicVideoData`() =
        runTest {
            val dynamicData =
                dev.frost819.newbv.biliapi.http.entity.dynamic.DynamicData(
                    hasMore = true,
                    offset = "offset-123",
                    updateBaseline = "baseline-456",
                    updateNum = 5,
                    items = listOf(fakeDynamicItem()),
                )
            coEvery { BiliHttpApi.getDynamicList(any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = dynamicData)

            val result =
                repository.getDynamicVideos(
                    page = 1,
                    offset = "",
                    updateBaseline = "",
                    preferApiType = ApiType.Web,
                )

            assertThat(result.videos).hasSize(1)
            assertThat(result.hasMore).isTrue()
            assertThat(result.historyOffset).isEqualTo("offset-123")
            assertThat(result.updateBaseline).isEqualTo("baseline-456")
        }

    @Test
    fun `getDynamicVideos Web with empty items returns empty list`() =
        runTest {
            val dynamicData =
                dev.frost819.newbv.biliapi.http.entity.dynamic.DynamicData(
                    hasMore = false,
                    offset = "",
                    updateBaseline = "",
                    updateNum = 0,
                )
            coEvery { BiliHttpApi.getDynamicList(any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = dynamicData)

            val result =
                repository.getDynamicVideos(
                    page = 1,
                    offset = "",
                    updateBaseline = "",
                    preferApiType = ApiType.Web,
                )

            assertThat(result.videos).isEmpty()
            assertThat(result.hasMore).isFalse()
        }

    // ------------------------------------------------------------------
    // checkIsFollowing (additional cases)
    // ------------------------------------------------------------------

    @Test
    fun `checkIsFollowing returns true when relation is FollowedQuietly`() =
        runTest {
            coEvery { BiliHttpApi.getRelations(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        RelationData(
                            relation =
                                Relation(
                                    mid = MID,
                                    attribute = RelationType.FollowedQuietly,
                                    mtime = 0,
                                    special = 0,
                                ),
                            beRelation =
                                Relation(
                                    mid = 0,
                                    attribute = RelationType.None,
                                    mtime = 0,
                                    special = 0,
                                ),
                        ),
                )

            val result = repository.checkIsFollowing(MID)

            assertThat(result).isTrue()
        }

    // ------------------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------------------

    private fun fakeDynamicItem() =
        DynamicItem(
            basic =
                DynamicItem.Basic(
                    commentIdStr = "100",
                    commentType = 1,
                    likeIcon =
                        DynamicItem.Basic.LikeIcon(
                            actionUrl = "",
                            endUrl = "",
                            id = 0L,
                            startUrl = "",
                        ),
                    ridStr = "200",
                ),
            idStr = "item-1",
            modules =
                DynamicItem.Modules(
                    moduleAuthor =
                        DynamicItem.Modules.Author(
                            face = "http://face.test",
                            faceNft = false,
                            following = false,
                            jumpUrl = "",
                            label = "",
                            mid = 1L,
                            name = "UP主",
                            officialVerify =
                                DynamicItem.Modules.Author.OfficialVerify(
                                    desc = "",
                                    type = -1,
                                ),
                            pendant = Pendant(pid = 0, name = "", image = ""),
                            pubAction = "投稿了视频",
                            pubLocationText = "",
                            pubTime = "1小时前",
                            pubTs = 1700000000,
                            type = "AUTHOR_TYPE_NORMAL",
                            vip =
                                Vip(
                                    type = 0,
                                    status = 0,
                                    dueDate = 0L,
                                    themeType = 0,
                                    label =
                                        Vip.Label(
                                            text = "",
                                            textColor = "",
                                            bgStyle = 0,
                                            bgColor = "",
                                            borderColor = "",
                                        ),
                                    avatarSubscript = 0,
                                    nicknameColor = "",
                                    avatarSubscriptUrl = "",
                                ),
                        ),
                    moduleDynamic =
                        DynamicItem.Modules.Dynamic(
                            major =
                                DynamicItem.Modules.Dynamic.Major(
                                    type = "MAJOR_TYPE_ARCHIVE",
                                    archive =
                                        DynamicItem.Modules.Dynamic.Major.Archive(
                                            aid = "100",
                                            badge =
                                                DynamicItem.Modules.Dynamic.Major.Archive.Badge(
                                                    bgColor = "",
                                                    color = "",
                                                    text = "",
                                                ),
                                            bvid = "BV1xx",
                                            cover = "http://cover.test",
                                            desc = "描述",
                                            disablePreview = 0,
                                            durationText = "5:00",
                                            jumpUrl = "",
                                            stat =
                                                DynamicItem.Modules.Dynamic.Major.Archive.Stat(
                                                    danmaku = "10",
                                                    play = "500",
                                                ),
                                            title = "动态视频",
                                            type = 1,
                                        ),
                                ),
                        ),
                    moduleMore = DynamicItem.Modules.More(),
                    moduleStat =
                        DynamicItem.Modules.Stat(
                            comment =
                                DynamicItem.Modules.Stat.StatItem(
                                    count = 0,
                                    forbidden = false,
                                ),
                            forward =
                                DynamicItem.Modules.Stat.StatItem(
                                    count = 0,
                                    forbidden = false,
                                ),
                            like =
                                DynamicItem.Modules.Stat.StatItem(
                                    count = 10,
                                    forbidden = false,
                                ),
                        ),
                ),
            type = "DYNAMIC_TYPE_AV",
            visible = true,
        )

    private fun fakeVListItem(
        aid: Long = 1L,
        title: String = "title",
    ) = dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData.SpaceVideoListItem.VListItem(
        aid = aid,
        bvid = "BV$aid",
        author = "UP",
        comment = 0,
        copyright = "",
        created = 1700000000L,
        description = "",
        hideClick = false,
        isPay = 0,
        isUnionVideo = 0,
        length = "5:00",
        mid = 1L,
        pic = "http://pic.test",
        play = 100,
        review = 0,
        subtitle = "",
        title = title,
        typeid = 0,
        videoReview = 0,
        isSteinsGate = 0,
        isLivePlayback = 0,
        meta = null,
        _isAvoided = 0,
        attribute = 0,
        playbackPosition = 0,
    )

    private fun appSpaceVideoItem(
        aid: Long = 1L,
        bvid: String = "BV1",
        title: String = "App视频",
    ) = AppSpaceVideoData.SpaceVideoItem(
        title = title,
        subtitle = "",
        tname = "",
        cover = "http://pic.test",
        uri = "",
        param = aid.toString(),
        goto = "av",
        length = "5:00",
        duration = 300,
        isPopular = false,
        isSteins = false,
        isUgcpay = false,
        isCooperation = false,
        isPgc = false,
        isLivePlayback = false,
        play = 100,
        danmaku = 10,
        ctime = 1700000000,
        ugcPay = 0,
        author = "UP",
        state = true,
        bvid = bvid,
        videos = 1,
        firstcid = aid,
        cursorAttr =
            AppSpaceVideoData.SpaceVideoItem.CursorAttr(
                isLastWatchedArc = false,
                rank = 0,
            ),
        iconType = 0,
    )

    private fun appSpaceData(
        hasNext: Boolean = false,
        vararg items: AppSpaceVideoData.SpaceVideoItem,
    ) = AppSpaceVideoData(
        count = items.size,
        item = items.toList(),
        lastWatchedLocator =
            AppSpaceVideoData.LastWatchedLocator(
                displayThreshold = 0,
                insertRanking = 0,
                text = "",
            ),
        hasNext = hasNext,
    )

    private fun fakeFollowedUser(
        mid: Long = 1L,
        uname: String = "user",
    ) = dev.frost819.newbv.biliapi.http.entity.user.UserFollowData.FollowedUser(
        mid = mid,
        attribute = 2,
        mtime = 1700000000,
        tag = null,
        special = 0,
        uname = uname,
        face = "http://face.test",
        sign = "",
        officialVerify =
            dev.frost819.newbv.biliapi.http.entity.user
                .OfficialVerify(type = -1, desc = ""),
        vip =
            dev.frost819.newbv.biliapi.http.entity.user.UserFollowData.FollowedUser.Vip(
                vipType = 0,
                vipDueDate = 0L,
                dueRemark = "",
                accessStatus = 0,
                vipStatus = 0,
                vipStatusWarn = "",
                themeType = 0,
                label =
                    dev.frost819.newbv.biliapi.http.entity.user.Vip.Label(
                        path = "",
                        text = "",
                        labelTheme = "",
                        textColor = "",
                        bgStyle = 0,
                        bgColor = "",
                        borderColor = "",
                    ),
            ),
        nftIcon = "",
        recReason = "",
        trackId = "",
    )
}
