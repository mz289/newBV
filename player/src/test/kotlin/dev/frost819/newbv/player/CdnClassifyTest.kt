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
}
