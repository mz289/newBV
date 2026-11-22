package dev.frost819.newbv.player

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** [CdnClassify] 的单元测试：候选地址的层级归入。 */
class CdnClassifyTest {
    @Test
    fun `upos mirrors are official`() {
        assertThat(CdnClassify.tierOf("https://upos-sz-mirrorali.bilivideo.com/upgcxcode/0/1/x/v.m4s?upsig=1"))
            .isEqualTo(CdnTier.OFFICIAL)
        assertThat(CdnClassify.tierOf("https://upos-sz-estgoss.bilivideo.com/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.OFFICIAL)
    }

    @Test
    fun `bcache pop hosts are official`() {
        assertThat(CdnClassify.tierOf("https://cn-sccd-cu-01-01.bilivideo.com/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.OFFICIAL)
    }

    @Test
    fun `akamai mirror is official`() {
        assertThat(CdnClassify.tierOf("https://upos-hz-mirrorakam.akamaized.net/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.OFFICIAL)
    }

    @Test
    fun `tf proxy hosts are proxy tier`() {
        assertThat(CdnClassify.tierOf("https://upos-tf-xy-x.bilivideo.com/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.PROXY)
        assertThat(CdnClassify.tierOf("https://proxy-tf-x.bilivideo.com/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.PROXY)
    }

    @Test
    fun `mcdn and szbdyd are p2p`() {
        assertThat(CdnClassify.tierOf("https://xy123.mcdn.bilivideo.com:4483/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.P2P)
        assertThat(CdnClassify.tierOf("https://xy212x234x112x234xy.szbdyd.com/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.P2P)
    }

    @Test
    fun `bare ip hosts are p2p`() {
        assertThat(CdnClassify.tierOf("https://1.2.3.4:99/v1/resource/x"))
            .isEqualTo(CdnTier.P2P)
    }

    @Test
    fun `302 pcdn subdomains are p2p`() {
        assertThat(CdnClassify.tierOf("https://upos-sz-302ppio.bilivideo.com/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.P2P)
        // mirror 形态但证书是 PCDN 的
        assertThat(CdnClassify.tierOf("https://upos-sz-mirror14b.bilivideo.com/upgcxcode/0/1/x/v.m4s"))
            .isEqualTo(CdnTier.P2P)
    }

    @Test
    fun `unknown hosts fall back to unknown tier`() {
        assertThat(CdnClassify.tierOf("https://example.com/some/other.m4s"))
            .isEqualTo(CdnTier.UNKNOWN)
        assertThat(CdnClassify.tierOf("not a url"))
            .isEqualTo(CdnTier.UNKNOWN)
    }

    @Test
    fun `tier ranks are ordered official before proxy before unknown before p2p`() {
        assertThat(CdnTier.OFFICIAL.rank).isLessThan(CdnTier.PROXY.rank)
        assertThat(CdnTier.PROXY.rank).isLessThan(CdnTier.UNKNOWN.rank)
        assertThat(CdnTier.UNKNOWN.rank).isLessThan(CdnTier.P2P.rank)
    }

    @Test
    fun `mirror host with os=mcdn is p2p`() {
        // 官方 mirror 外壳的 PCDN（PR #31 classifyCdnUrl 的 `os=mcdn` 判定）
        assertThat(
            CdnClassify.tierOf(
                "https://upos-sz-mirrorcoso1.bilivideo.com/upgcxcode/0/1/x/v.m4s?os=mcdn&upsig=1",
            ),
        ).isEqualTo(CdnTier.P2P)
    }

    @Test
    fun `custom pcdn domain with upgcxcode path and os=mcdn is p2p`() {
        // 实测出现过的形态：任意自有域名 + /upgcxcode 路径 + os=mcdn + mcdnid
        assertThat(
            CdnClassify.tierOf(
                "https://hze5261c.edge.mountaintoys.cn:4483/upgcxcode/00/66/43750526600/43750526600-1-30080.m4s?os=mcdn&mcdnid=50058993&sche=rspull",
            ),
        ).isEqualTo(CdnTier.P2P)
    }

    @Test
    fun `mcdn tf resource path is p2p`() {
        assertThat(CdnClassify.tierOf("https://xy-xxx.mcdn.bilivideo.com:4483/v1/resource/12345?upwsig=1"))
            .isEqualTo(CdnTier.P2P)
    }

    @Test
    fun `video key comes from upgcxcode cid segment`() {
        assertThat(
            CdnClassify.videoKeyOf("https://upos-sz-mirrorali.bilivideo.com/upgcxcode/00/66/43750526600/43750526600-1-30080.m4s?x=1"),
        ).isEqualTo("43750526600")
        // 音频轨道同 cid → 同 key（样本按视频隔离）
        assertThat(
            CdnClassify.videoKeyOf("https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/00/66/43750526600/43750526600-30216.m4s"),
        ).isEqualTo("43750526600")
    }

    @Test
    fun `media path key is host independent`() {
        val key =
            CdnClassify.mediaPathKey("https://a.bilivideo.com/upgcxcode/0/1/x/v.m4s?upsig=sig&deadline=1")
        assertThat(key).isEqualTo("/upgcxcode/0/1/x/v.m4s?upsig=sig&deadline=1")
        assertThat(CdnClassify.mediaPathKey("https://other.bilivideo.com" + key)).isEqualTo(key)
    }
}
