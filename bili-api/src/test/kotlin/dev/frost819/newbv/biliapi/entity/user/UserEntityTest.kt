package dev.frost819.newbv.biliapi.entity.user

import bilibili.app.archive.v1.author
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [FollowedUser]、[Author] 实体 HTTP/gRPC→Domain 转换方法的单元测试。
 */
class UserEntityTest {
    @Test
    fun `FollowedUser fromHttpFollowedUser maps mid uname face sign`() {
        val followedUser =
            dev.frost819.newbv.biliapi.http.entity.user.UserFollowData.FollowedUser(
                mid = 999L,
                attribute = 2,
                mtime = 1700000000,
                tag = null,
                special = 0,
                uname = "关注用户",
                face = "http://face.test",
                sign = "签名内容",
                officialVerify = fakeOfficialVerify(),
                vip = fakeFollowedUserVip(),
                nftIcon = "",
                recReason = "",
                trackId = "t1",
            )

        val result = FollowedUser.fromHttpFollowedUser(followedUser)

        assertThat(result.mid).isEqualTo(999L)
        assertThat(result.name).isEqualTo("关注用户")
        assertThat(result.avatar).isEqualTo("http://face.test")
        assertThat(result.sign).isEqualTo("签名内容")
    }

    // ------------------------------------------------------------------
    // Author.fromAuthor(gRPC)
    // ------------------------------------------------------------------

    @Test
    fun `Author fromAuthor gRPC maps mid name face`() {
        val grpcAuthor =
            author {
                mid = 456L
                name = "gRPC UP主"
                face = "http://grpc-face.test"
            }

        val author = Author.fromAuthor(grpcAuthor)

        assertThat(author.mid).isEqualTo(456L)
        assertThat(author.name).isEqualTo("gRPC UP主")
        assertThat(author.face).isEqualTo("http://grpc-face.test")
    }

    @Test
    fun `Author fromAuthor gRPC with default values`() {
        val grpcAuthor = author { }

        val author = Author.fromAuthor(grpcAuthor)

        assertThat(author.mid).isEqualTo(0L)
        assertThat(author.name).isEqualTo("")
        assertThat(author.face).isEqualTo("")
    }

    private fun fakeOfficialVerify() =
        dev.frost819.newbv.biliapi.http.entity.user
            .OfficialVerify(type = -1, desc = "")

    private fun fakeFollowedUserVip() =
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
        )
}
