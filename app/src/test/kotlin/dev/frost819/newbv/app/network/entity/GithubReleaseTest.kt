package dev.frost819.newbv.app.network.entity

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.network.UpdateChannel
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * [GithubRelease] 序列化/反序列化的单元测试。
 */
class GithubReleaseTest {
    private val json =
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
        }

    /** 构造完整 GitHub 响应 JSON（含未建模字段，验证其被忽略）。 */
    private fun releaseJson(
        name: String,
        draft: Boolean = false,
        assetsJson: String = "[]",
    ) = """
        {
            "assets": $assetsJson,
            "assets_url": "https://api.github.com/repos/test/test/releases/1/assets",
            "body": "Release notes",
            "created_at": "2024-01-01T00:00:00Z",
            "draft": $draft,
            "html_url": "https://github.com/test/test/releases/tag/$name",
            "id": 1,
            "name": "$name",
            "prerelease": true,
            "published_at": "2024-01-01T00:00:00Z",
            "tag_name": "$name",
            "tarball_url": "https://api.github.com/repos/test/test/tarball/$name",
            "target_commitish": "main",
            "upload_url": "https://uploads.github.com/repos/test/test/releases/1/assets",
            "url": "https://api.github.com/repos/test/test/releases/1",
            "zipball_url": "https://api.github.com/repos/test/test/zipball/$name",
            "unknown_field": "should be ignored"
        }
        """.trimIndent()

    /** 构造完整 Asset 响应 JSON（含未建模字段）。 */
    private fun assetJson(name: String) =
        """
        {
            "browser_download_url": "https://github.com/test/test/releases/download/v1.0/$name",
            "content_type": "application/vnd.android.package-archive",
            "download_count": 100,
            "id": 100,
            "name": "$name",
            "size": 42000000,
            "state": "uploaded",
            "url": "https://api.github.com/repos/test/test/releases/assets/100"
        }
        """.trimIndent()

    @Test
    fun `deserialize keeps only modeled fields and ignores the rest`() {
        val release = json.decodeFromString<GithubRelease>(releaseJson("v1.0"))

        assertThat(release.name).isEqualTo("v1.0")
        assertThat(release.body).isEqualTo("Release notes")
        assertThat(release.draft).isFalse()
        assertThat(release.assets).isEmpty()
    }

    @Test
    fun `deserialize draft flag`() {
        val release = json.decodeFromString<GithubRelease>(releaseJson("v2.0", draft = true))

        assertThat(release.draft).isTrue()
    }

    @Test
    fun `deserialize assets with unmapped fields ignored`() {
        val release =
            json.decodeFromString<GithubRelease>(
                releaseJson("v1.0", assetsJson = "[${assetJson("BV_1.apk")}]"),
            )

        assertThat(release.assets).hasSize(1)
        val asset = release.assets[0]
        assertThat(asset.name).isEqualTo("BV_1.apk")
        assertThat(asset.browserDownloadUrl).isEqualTo("https://github.com/test/test/releases/download/v1.0/BV_1.apk")
        assertThat(asset.size).isEqualTo(42000000)
    }

    @Test
    fun `deserialize list of releases`() {
        val releases =
            json.decodeFromString<List<GithubRelease>>(
                "[${releaseJson("v1.0")}, ${releaseJson("v2.0")}]",
            )

        assertThat(releases).hasSize(2)
        assertThat(releases[0].name).isEqualTo("v1.0")
        assertThat(releases[1].name).isEqualTo("v2.0")
    }

    @Test
    fun `roundtrip preserves all fields`() {
        val original =
            GithubRelease(
                assets =
                    listOf(
                        GithubRelease.Asset(
                            browserDownloadUrl = "https://example.com/download.apk",
                            name = "newBV_1_0.0.1_release.apk",
                            size = 50000000,
                        ),
                    ),
                body = "Release body text",
                draft = false,
                name = "newBV 0.0.1",
            )

        val serialized = json.encodeToString(GithubRelease.serializer(), original)
        val deserialized = json.decodeFromString<GithubRelease>(serialized)

        assertThat(deserialized).isEqualTo(original)
    }

    // ── 更新包选取与版本解析 ──────────────────────────────────────────

    private fun asset(name: String) =
        GithubRelease.Asset(
            browserDownloadUrl = "https://example.com/$name",
            name = name,
            size = 1024,
        )

    private fun releaseWithAssets(assets: List<GithubRelease.Asset>) =
        GithubRelease(
            assets = assets,
            body = "",
            draft = false,
            name = "release",
        )

    @Test
    fun `findApkAsset matches channel keyword`() {
        val release =
            releaseWithAssets(
                listOf(
                    asset("newBV_646_0.1.0.r646.abc1234_release.apk"),
                    asset("newBV_647_0.1.0.r647.def5678_debug.apk"),
                ),
            )

        assertThat(release.findApkAsset(UpdateChannel.RELEASE.assetKeywords)?.name)
            .isEqualTo("newBV_646_0.1.0.r646.abc1234_release.apk")
        assertThat(release.findApkAsset(UpdateChannel.DEBUG.assetKeywords)?.name)
            .isEqualTo("newBV_647_0.1.0.r647.def5678_debug.apk")
    }

    @Test
    fun `findApkAsset ignores assets without newBV prefix`() {
        val release =
            releaseWithAssets(
                listOf(asset("app-release.apk"), asset("something_debug.apk")),
            )

        assertThat(release.findApkAsset(UpdateChannel.RELEASE.assetKeywords)).isNull()
        assertThat(release.findApkAsset(UpdateChannel.DEBUG.assetKeywords)).isNull()
    }

    @Test
    fun `findApkAsset returns null when no asset matches channel`() {
        val release = releaseWithAssets(listOf(asset("newBV_1_0.0.1_debug.apk")))

        assertThat(release.findApkAsset(UpdateChannel.RELEASE.assetKeywords)).isNull()
    }

    @Test
    fun `findApkAsset returns null for empty assets`() {
        val release = releaseWithAssets(emptyList())

        assertThat(release.findApkAsset(UpdateChannel.RELEASE.assetKeywords)).isNull()
        assertThat(release.findApkAsset(UpdateChannel.DEBUG.assetKeywords)).isNull()
    }

    @Test
    fun `parseVersionCode parses code from asset name`() {
        val release = releaseWithAssets(listOf(asset("newBV_647_0.1.0.r647.abc1234_debug.apk")))

        assertThat(release.findApkAsset(UpdateChannel.DEBUG.assetKeywords)?.parseVersionCode())
            .isEqualTo(647)
    }

    @Test
    fun `parseVersionCode returns null for malformed asset name`() {
        assertThat(asset("newBV_debug_abc.apk").parseVersionCode()).isNull()
        assertThat(asset("random.apk").parseVersionCode()).isNull()
        assertThat(asset("newBV_.apk").parseVersionCode()).isNull()
    }
}
