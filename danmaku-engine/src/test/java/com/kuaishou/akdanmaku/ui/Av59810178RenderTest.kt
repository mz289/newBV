package com.kuaishou.akdanmaku.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.SpecialDanmakuParser
import com.kuaishou.akdanmaku.render.ModeAwareRenderer
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * av59810178（BV1Ft411n7B6「高级弹幕施工现场」绯色月下 c75+c77，cid 104193220）
 * 真实弹幕离线渲染胶片。
 *
 * 数据：`av59810178_m7.json`，App 接口（gRPC `DM.DmSegMobile`）导出的全量 1608 条
 * （1597 条 mode 7 + 11 条普通弹幕）。渲染管线与线上一致：
 * 解析 → DanmakuItemData → updateData → 60fps 推进 → draw 到 2560×1440 位图。
 *
 * 输出：`danmaku-engine/build/av59810178-render/<tag>.png` + 每帧在场弹幕与墨迹分布报告。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Av59810178RenderTest {
    private val displayWidth = 2560
    private val displayHeight = 1440

    private data class Entry(
        val timeMs: Long,
        val mode: Int,
        val size: Int,
        val color: Int,
        val payload: String,
    )

    private fun load(): List<Entry> {
        val json =
            javaClass.classLoader!!
                .getResourceAsStream("av59810178_m7.json")!!
                .readBytes()
                .decodeToString()
        val rows = JSONObject(json).getJSONArray("entries")
        return List(rows.length()) { i ->
            val row = rows.getJSONObject(i)
            Entry(
                timeMs = (row.getDouble("t") * 1000).toLong(),
                mode = row.getInt("mode"),
                size = row.getInt("size"),
                color = row.getInt("color"),
                payload = row.getString("payload"),
            )
        }
    }

    private fun toData(entries: List<Entry>): Pair<List<DanmakuItemData>, Int> {
        var dropped = 0
        val items =
            entries.mapNotNull { entry ->
                val mode =
                    when (entry.mode) {
                        4 -> DanmakuItemData.DANMAKU_MODE_CENTER_BOTTOM
                        5 -> DanmakuItemData.DANMAKU_MODE_CENTER_TOP
                        7 -> DanmakuItemData.DANMAKU_MODE_SPECIAL
                        else -> DanmakuItemData.DANMAKU_MODE_ROLLING
                    }
                if (mode == DanmakuItemData.DANMAKU_MODE_SPECIAL) {
                    val spec = SpecialDanmakuParser.parse(entry.payload)
                    if (spec == null) {
                        dropped++
                        return@mapNotNull null
                    }
                    DanmakuItemData(
                        danmakuId = entry.payload.hashCode().toLong() and 0x7FFFFFFF,
                        position = entry.timeMs,
                        content = spec.text,
                        mode = mode,
                        textSize = entry.size,
                        // App 层规则：color 0 → 白色（官方 p.color ? parse : WHITE）
                        textColor = (if (entry.color == 0) 0xFFFFFF else entry.color) or -0x1000000,
                        lifeTime = spec.visibleDurationMs,
                        special = spec,
                    )
                } else {
                    DanmakuItemData(
                        danmakuId = entry.payload.hashCode().toLong() and 0x7FFFFFFF,
                        position = entry.timeMs,
                        content = entry.payload,
                        mode = mode,
                        textSize = entry.size,
                        textColor = (if (entry.color == 0) 0xFFFFFF else entry.color) or -0x1000000,
                    )
                }
            }
        return items to dropped
    }

    private fun fakeDisplayer(): DanmakuDisplayer =
        object : DanmakuDisplayer {
            override var width: Int = displayWidth
            override var height: Int = displayHeight
            override val margin: Int = 4
            override val allMarginTop: Float = 0f
            override val density: Float = 2f
            override val scaleDensity: Float = 2f
            override val densityDpi: Int = 320
        }

    @Suppress("SameParameterValue")
    private fun renderStrip(
        tag: String,
        entries: List<Entry>,
        fromMs: Long,
        toMs: Long,
        frameStepMs: Long,
    ) {
        val (data, dropped) = toData(entries)
        println("[%s] 载荷 %d 条，解析成功 %d，丢弃 %d".format(tag, entries.size, data.size, dropped))
        assertThat(dropped).isEqualTo(0)
        val player = DanmakuPlayer(ModeAwareRenderer())
        player.engine.context.displayer = fakeDisplayer()
        val outDir = File("build/av59810178-render").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888)
        try {
            player.updateData(data)
            player.engine.start()
            player.engine.seekTo(fromMs - 1_500L) // 提前起播，保证实体先于弹幕创建
            var nextShot = fromMs
            val frameMs = 1000L / 60L
            var t = fromMs - 1_500L
            while (t <= toMs + frameMs) {
                player.engine.step(frameMs / 1000f)
                player.engine.preAct()
                player.engine.act()
                t = player.engine.getCurrentTimeMs()
                if (t >= nextShot) {
                    player.engine.draw(Canvas(bitmap)) {}
                    val name = "%s-%03ds.png".format(tag, nextShot / 1000)
                    File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    println(
                        "[%s-%03ds] 在场 %d 条；墨迹分布=%s".format(
                            tag,
                            nextShot / 1000,
                            player.engine.entities.size(),
                            inkProfile(bitmap),
                        ),
                    )
                    nextShot += frameStepMs
                }
            }
        } finally {
            player.release()
            bitmap.recycle()
        }
    }

    /** 每 1/10 屏高的墨迹占比（画布只画弹幕，alpha > 0 即墨迹）。 */
    private fun inkProfile(bitmap: Bitmap): String {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val bandTotal = IntArray(10)
        val bandInk = IntArray(10)
        var y = 0
        while (y < h) {
            val band = y * 10 / h
            var x = 0
            while (x < w) {
                bandTotal[band]++
                if ((pixels[y * w + x] ushr 24) != 0) bandInk[band]++
                x += 2
            }
            y += 2
        }
        return (0 until 10).joinToString(" ") { i ->
            if (bandTotal[i] == 0) "0%" else "%.1f%%".format(100.0 * bandInk[i] / bandTotal[i])
        }
    }

    @Test
    fun `render dense advanced danmaku windows`() {
        val entries = load()
        assertThat(entries.count { it.mode == 7 }).isEqualTo(1597)
        // 高潮三连（200-230s / 300-325s / 400-430s 三个密集窗口）+ 单发密集段
        renderStrip("climax1", entries, fromMs = 205_000L, toMs = 210_000L, frameStepMs = 1_000L)
        renderStrip("climax2", entries, fromMs = 300_000L, toMs = 306_000L, frameStepMs = 1_000L)
        renderStrip("climax3", entries, fromMs = 405_000L, toMs = 410_000L, frameStepMs = 1_000L)
        renderStrip("bridge", entries, fromMs = 350_000L, toMs = 353_000L, frameStepMs = 1_000L)
        renderStrip("outro", entries, fromMs = 535_000L, toMs = 538_000L, frameStepMs = 1_000L)
    }
}
