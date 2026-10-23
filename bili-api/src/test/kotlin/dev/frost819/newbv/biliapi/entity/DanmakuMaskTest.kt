package dev.frost819.newbv.biliapi.entity

import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMask
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMaskSegment
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMaskType
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMobMaskFrame
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuWebMaskFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DanmakuMaskTest {
    @Test
    fun `web mask segments decode on demand with SVG frames`() {
        val mask = loadMask("webmask", DanmakuMaskType.WebMask)
        forEachSegment(mask) { segment ->
            assertTrue(segment.frames.isNotEmpty())
            segment.frames.forEach { frame ->
                assertTrue(frame is DanmakuWebMaskFrame)
                assertTrue(frame.svg.contains("<svg"))
            }
        }
    }

    @Test
    fun `mobile mask segments decode on demand with packed bitmaps`() {
        val mask = loadMask("mobmask", DanmakuMaskType.MobMask)
        forEachSegment(mask) { segment ->
            assertTrue(segment.frames.isNotEmpty())
            segment.frames.forEach { frame ->
                assertTrue(frame is DanmakuMobMaskFrame)
                assertTrue(frame.width > 0 && frame.height > 0)
                assertEquals((frame.width * frame.height + 7) / 8, frame.image.size)
            }
        }
    }

    private fun loadMask(extension: String, type: DanmakuMaskType): DanmakuMask =
        DanmakuMask.fromStream(
            checkNotNull(javaClass.getResourceAsStream("/35496788838_30_0.$extension")),
            type,
        )

    private fun forEachSegment(mask: DanmakuMask, verify: (DanmakuMaskSegment) -> Unit) {
        assertTrue(mask.segmentCount > 0)
        var position = 0L
        repeat(mask.segmentCount) {
            val segment = assertNotNull(mask.getSegmentAt(position))
            assertEquals(position, segment.range.first)
            verify(segment)
            position = segment.range.last + 1
        }
        assertEquals(Long.MAX_VALUE, position)
    }

    @Test
    fun `non-final web mask segments cover the last millisecond`() {
        val mask = loadMask("webmask", DanmakuMaskType.WebMask)
        forEachSegment(mask) { segment ->
            if (segment.range.last + 1 != Long.MAX_VALUE) {
                assertTrue(segment.frames.any { segment.range.last in it.range })
            }
        }
    }

    @Test
    fun `final web mask frame ends with the actual data`() {
        val mask = loadMask("webmask", DanmakuMaskType.WebMask)
        val segment = assertNotNull(mask.getSegmentAt(Long.MAX_VALUE - 1))
        assertTrue(segment.frames.last().range.last < Long.MAX_VALUE - 1)
    }
}
