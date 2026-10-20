package com.kuaishou.akdanmaku.ui

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.SpecialDanmakuParser
import com.kuaishou.akdanmaku.ext.action
import com.kuaishou.akdanmaku.ext.dataComponent
import com.kuaishou.akdanmaku.render.ModeAwareRenderer
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * 真实弹幕数据（av59810178「高级弹幕施工现场」绯色月下 c75+c77，cid 104193220，
 * 全量 1608 条，其中 1597 条 mode 7）的引擎回放性能测试。
 *
 * 流程与生产一致：载荷解析 → DanmakuItemData → updateData → 引擎按帧推进
 * （preAct → step → act）。两阶段：
 * - 全时间轴粗粒度扫描（1s/帧，覆盖分段切片与实体生命周期）
 * - 高级弹幕密集窗口（约 5:00-5:15 的副歌连发）按 30fps 密集推进
 *
 * 断言只做功能性与宽松的性能上界（Robolectric 环境耗时波动大），
 * 具体数值打印到 stdout 供对比。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpecialDanmakuCorpusPlaybackPerfTest {
    private data class Entry(
        val timeMs: Long,
        val mode: Int,
        val size: Int,
        val color: Int,
        val payload: String,
    )

    private fun loadCorpus(name: String): List<Entry> {
        val json =
            javaClass.classLoader!!
                .getResourceAsStream(name)!!
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

    /** 与 DanmakuViewModel.toDanmakuItemData 相同的转换逻辑。 */
    private fun toData(entry: Entry): DanmakuItemData? {
        val mode =
            when (entry.mode) {
                4 -> DanmakuItemData.DANMAKU_MODE_CENTER_BOTTOM
                5 -> DanmakuItemData.DANMAKU_MODE_CENTER_TOP
                7 -> DanmakuItemData.DANMAKU_MODE_SPECIAL
                else -> DanmakuItemData.DANMAKU_MODE_ROLLING
            }
        if (mode != DanmakuItemData.DANMAKU_MODE_SPECIAL) {
            return DanmakuItemData(
                danmakuId = entry.payload.hashCode().toLong() and 0x7FFFFFFF,
                position = entry.timeMs,
                content = entry.payload,
                mode = mode,
                textSize = entry.size,
                textColor = (if (entry.color == 0) 0xFFFFFF else entry.color) or -0x1000000,
            )
        }
        val spec = SpecialDanmakuParser.parse(entry.payload) ?: return null
        return DanmakuItemData(
            danmakuId = entry.payload.hashCode().toLong() and 0x7FFFFFFF,
            position = entry.timeMs,
            content = spec.text,
            mode = mode,
            textSize = entry.size,
            textColor = (if (entry.color == 0) 0xFFFFFF else entry.color) or -0x1000000,
            lifeTime = spec.visibleDurationMs,
            special = spec,
        )
    }

    @Test
    fun `parse all real special payloads`() {
        val corpus = loadCorpus("av59810178_m7.json")
        val specialEntries = corpus.filter { it.mode == 7 }

        val start = System.nanoTime()
        val parsed = specialEntries.mapNotNull(::toData)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertThat(corpus).hasSize(1608)
        assertThat(specialEntries).hasSize(1597)
        // 全部真实 mode 7 载荷都应可解析
        assertThat(parsed).hasSize(1597)
        parsed.forEach { data ->
            assertThat(data.mode).isEqualTo(DanmakuItemData.DANMAKU_MODE_SPECIAL)
            assertThat(data.lifeTime).isGreaterThan(0L)
        }
        println(
            String.format(
                Locale.US,
                "[Perf] parse 1597 real special payloads: %d ms (%.3f ms/条)",
                elapsedMs,
                elapsedMs / specialEntries.size.toDouble(),
            ),
        )
    }

    @Test
    fun `playback over real corpus keeps frame cost bounded`() {
        val corpus = loadCorpus("av59810178_m7.json")
        val parseStart = System.nanoTime()
        val data = corpus.mapNotNull(::toData)
        val parseMs = (System.nanoTime() - parseStart) / 1_000_000

        val player = DanmakuPlayer(ModeAwareRenderer())
        player.engine.context.displayer = fakeDisplayer()

        val loadStart = System.nanoTime()
        player.updateData(data)
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000

        var actCostMaxNs = 0L
        var actCostTotalNs = 0L
        var frames = 0

        fun runFrame(deltaSeconds: Float) {
            player.engine.step(deltaSeconds)
            val frameStart = System.nanoTime()
            player.engine.preAct()
            player.engine.act()
            val cost = System.nanoTime() - frameStart
            actCostTotalNs += cost
            actCostMaxNs = maxOf(actCostMaxNs, cost)
            frames++
        }

        // 阶段一：全时间轴 1s/帧粗扫（覆盖分段切片、实体创建与超时移除）
        player.engine.start()
        val coarseStart = System.nanoTime()
        while (player.engine.getCurrentTimeMs() < MAX_CORPUS_TIME_MS) {
            runFrame(1f)
        }
        val coarseMs = (System.nanoTime() - coarseStart) / 1_000_000

        // 阶段二：高级弹幕密集窗口（约 5:00 起的副歌连发）30fps 密集推进
        player.engine.seekTo(DENSE_WINDOW_START_MS)
        var specialSeen = 0
        var specialWithPosition = 0
        val denseStart = System.nanoTime()
        repeat((DENSE_WINDOW_MS / FRAME_INTERVAL_MS).toInt()) {
            runFrame(FRAME_INTERVAL_MS / 1000f)
            val nowMs = player.engine.getCurrentTimeMs()
            player.engine.entities.forEach { entity ->
                val item = entity.dataComponent?.item ?: return@forEach
                val action = entity.action ?: return@forEach
                // ActionSystem 的时间门控（入场后动作开始推进）
                if (item.data.mode == DanmakuItemData.DANMAKU_MODE_SPECIAL && nowMs >= item.data.position) {
                    specialSeen++
                    val first = item.data.special!!.keyPoints.first()
                    // 非零起点必须落到缩放后的位置上
                    if (first[0] != 0f || first[1] != 0f) {
                        if (action.position.x != 0f || action.position.y != 0f) {
                            specialWithPosition++
                        }
                    }
                }
            }
        }
        val denseMs = (System.nanoTime() - denseStart) / 1_000_000

        val avgFrameUs = actCostTotalNs / 1000f / frames
        val maxFrameMs = actCostMaxNs / 1_000_000
        println(
            String.format(
                Locale.US,
                "[Perf] corpus playback: %d entries, parse=%d ms, load=%d ms, " +
                    "coarse %d ms + dense %d ms for %d frames: avg frame=%.0f us, max frame=%d ms",
                data.size,
                parseMs,
                loadMs,
                coarseMs,
                denseMs,
                frames,
                avgFrameUs,
                maxFrameMs,
            ),
        )

        // 功能断言
        assertThat(data.size).isEqualTo(1608)
        assertThat(player.engine.entities.size()).isGreaterThan(0)
        // 密集窗口内高级弹幕入场且带非零落位
        assertThat(specialSeen).isGreaterThan(0)
        assertThat(specialWithPosition).isGreaterThan(0)
        // 宽松性能上界（Robolectric 无真实渲染，仅防病态回退）
        assertThat(avgFrameUs).isLessThan(50_000f)
        assertThat(maxFrameMs).isLessThan(2_000L)

        player.release()
    }

    private fun fakeDisplayer(): DanmakuDisplayer =
        object : DanmakuDisplayer {
            override var width: Int = 1280
            override var height: Int = 720
            override val margin: Int = 4
            override val allMarginTop: Float = 0f
            override val density: Float = 2f
            override val scaleDensity: Float = 2f
            override val densityDpi: Int = 320
        }

    companion object {
        private const val FRAME_INTERVAL_MS = 33.33f
        private const val MAX_CORPUS_TIME_MS = 640_000L

        /** 高级弹幕密集窗口（约 5:00 起的副歌连发）。 */
        private const val DENSE_WINDOW_START_MS = 300_000L
        private const val DENSE_WINDOW_MS = 15_000L
    }

    /** BV1u4HVzZEno（弹幕还原緋色月下，5450 条 mode 7，64 分钟）的回放压力。 */
    @Test
    fun `playback over bv1u4 corpus keeps frame cost bounded`() {
        val corpus = loadCorpus("bv1u4_m7.json")
        val data = corpus.mapNotNull(::toData)
        val player = DanmakuPlayer(ModeAwareRenderer())
        player.engine.context.displayer = fakeDisplayer()
        val bitmap = android.graphics.Bitmap.createBitmap(1280, 720, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            player.updateData(data)
            player.engine.start()
            // 全时间轴 1s/帧粗扫（3857s）
            var coarseMax = 0L
            while (player.engine.getCurrentTimeMs() < 3_860_000L) {
                val start = System.nanoTime()
                player.engine.step(1f)
                player.engine.preAct()
                player.engine.act()
                coarseMax = maxOf(coarseMax, System.nanoTime() - start)
            }
            // 最密分钟（240s，2338 条）30fps 密集推进并真实绘制
            player.engine.seekTo(239_000)
            var denseMax = 0L
            var maxEntities = 0
            repeat((30_000 / FRAME_INTERVAL_MS).toInt()) {
                player.engine.step(FRAME_INTERVAL_MS / 1000f)
                player.engine.preAct()
                player.engine.act()
                val start = System.nanoTime()
                player.engine.draw(android.graphics.Canvas(bitmap)) {}
                denseMax = maxOf(denseMax, System.nanoTime() - start)
                maxEntities = maxOf(maxEntities, player.engine.entities.size())
            }
            println(
                String.format(
                    Locale.US,
                    "[Perf] bv1u4: entries=%d coarseMax=%d ms, denseMax=%.1f ms, maxEntities=%d",
                    data.size,
                    coarseMax / 1_000_000,
                    denseMax / 1_000_000.0,
                    maxEntities,
                ),
            )
            // 5450 条中 5449 条过官方门槛（1 条 5 字段载荷双端一致丢弃）
            assertThat(data.size).isEqualTo(5449)
            assertThat(maxEntities).isGreaterThan(0)
        } finally {
            player.release()
            bitmap.recycle()
        }
    }
}