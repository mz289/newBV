package dev.frost819.newbv.danmaku.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [DanmakuPinyinEncoder] 拼音 token 编码测试。
 */
class DanmakuPinyinEncoderTest {
    @Test
    fun `同音字共享主读音 token`() {
        // 小/晓 同音（xiǎo）：主读音 token 一致
        assertThat(DanmakuPinyinEncoder.encode("小")[0])
            .isEqualTo(DanmakuPinyinEncoder.encode("晓")[0])
        // 在/再 同音（zài）
        assertThat(DanmakuPinyinEncoder.encode("在")[0])
            .isEqualTo(DanmakuPinyinEncoder.encode("再")[0])
    }

    @Test
    fun `字典覆盖常用汉字`() {
        // 你好 都能查到读音：token 全部落进 0xE000 起的私用区
        // （好 是多音字 hǎo/hào，带次读音，token 数量不必为 1:1）
        val tokens = DanmakuPinyinEncoder.encode("你好")
        assertThat(tokens.toList()).isNotEmpty()
        assertThat(tokens.all { it >= '\uE000' }).isTrue()
    }

    @Test
    fun `非汉字走原样保留且大写转小写`() {
        assertThat(DanmakuPinyinEncoder.encode("A")).isEqualTo(charArrayOf('a'))
        assertThat(DanmakuPinyinEncoder.encode("！")).isEqualTo(charArrayOf('！'))
    }

    @Test
    fun `同文本命中编码缓存`() {
        assertThat(DanmakuPinyinEncoder.encode("前方高能"))
            .isSameInstanceAs(DanmakuPinyinEncoder.encode("前方高能"))
    }
}
