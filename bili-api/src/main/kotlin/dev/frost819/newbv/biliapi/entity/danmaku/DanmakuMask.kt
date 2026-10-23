package dev.frost819.newbv.biliapi.entity.danmaku

import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.decodeBase64
import okio.GzipSource
import okio.buffer
import okio.source
import java.io.InputStream

data class DanmakuMaskSegment(
    val range: LongRange,
    val frames: List<DanmakuMaskFrame>,
)

sealed class DanmakuMaskFrame(
    open val range: LongRange,
)

data class DanmakuWebMaskFrame(
    override val range: LongRange,
    val svg: String,
) : DanmakuMaskFrame(range)

data class DanmakuMobMaskFrame(
    override val range: LongRange,
    val width: Int,
    val height: Int,
    val image: ByteArray,
) : DanmakuMaskFrame(range) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DanmakuMobMaskFrame) return false
        return range == other.range &&
            width == other.width &&
            height == other.height &&
            image.contentEquals(other.image)
    }

    override fun hashCode(): Int {
        var r = range.hashCode()
        r = 31 * r + width
        r = 31 * r + height
        r = 31 * r + image.contentHashCode()
        return r
    }
}

/** 一个时间段的压缩蒙版数据。 */
private data class SegmentEntry(
    // segment 对应的播放时间范围
    val segRange: LongRange,
    // 该 segment 的 gzip 压缩块（原始字节，未解压）
    val compressedBytes: ByteArray,
)

private fun DanmakuMaskFrame.withEnd(end: Long): DanmakuMaskFrame =
    when (this) {
        is DanmakuWebMaskFrame -> copy(range = range.first until end)
        is DanmakuMobMaskFrame -> copy(range = range.first until end)
    }

/** 保存压缩数据，仅在 [getSegmentAt] 时解压当前段；解压结果由调用方管理。 */
class DanmakuMask private constructor(
    val type: DanmakuMaskType,
    private val entries: List<SegmentEntry>,
) {
    /**
     * 解压并返回当前播放时间（ms）对应的 segment。
     * 每次调用只解压一个 segment，解压结果不被 DanmakuMask 持有，用完可被 GC。
     */
    fun getSegmentAt(positionMs: Long): DanmakuMaskSegment? {
        val entry = entries.firstOrNull { positionMs in it.segRange } ?: return null
        return decompressEntry(entry)
    }

    val segmentCount: Int get() = entries.size

    private fun decompressEntry(
        entry: SegmentEntry,
    ): DanmakuMaskSegment {
        val compressedBuffer = Buffer().write(entry.compressedBytes)
        val frames = mutableListOf<DanmakuMaskFrame>()
        var lastTime = entry.segRange.first // 注：segment 内帧时间是连续的，首帧 range 起点在解压时确定

        GzipSource(compressedBuffer).buffer().use { gz ->
            when (type) {
                DanmakuMaskType.WebMask -> {
                    while (!gz.exhausted()) {
                        val svgLength = gz.readInt().toLong()
                        val time = gz.readLong()
                        gz.require(svgLength)
                        val raw = gz.readUtf8(svgLength)
                        val commaIdx = raw.indexOf(',')
                        val b64 =
                            (if (commaIdx != -1) raw.substring(commaIdx + 1) else raw)
                                .replace("\n", "")
                        val svg = b64.decodeBase64()?.utf8() ?: ""
                        frames.add(DanmakuWebMaskFrame(range = lastTime until time, svg = svg))
                        lastTime = time
                    }
                }

                DanmakuMaskType.MobMask -> {
                    while (!gz.exhausted()) {
                        val width = gz.readShort().toInt()
                        val height = gz.readShort().toInt()
                        val time = gz.readLong()
                        val imageSize = (width * height + 7) / 8
                        gz.require(imageSize.toLong())
                        val image = gz.readByteArray(imageSize.toLong()) // 仅当前帧
                        frames.add(DanmakuMobMaskFrame(lastTime until time, width, height, image))
                        lastTime = time
                    }
                }
            }
        }

        // 关闭段尾空洞：webmask 每段约 10s，段内帧按 ~33ms（30fps）推进，但服务端在该段
        // 通常少生成最后一帧，使最后帧结束时间比下一段起始时间早一帧（~33ms）。若不补齐，
        // findFrame 在该空洞内返回 null，蒙版会在每段边界短暂消失（表现为每隔数秒闪烁一下）。
        // segRange.last + 1 恰为下一段起始时间；仅末段等于 Long.MAX_VALUE，此时不能延伸，
        // 否则蒙版会在蒙版数据结束后一直残留。
        val segmentEnd = entry.segRange.last + 1
        if (segmentEnd != Long.MAX_VALUE && frames.isNotEmpty()) {
            val last = frames.last()
            frames[frames.lastIndex] = last.withEnd(maxOf(last.range.last + 1, segmentEnd))
        }

        return DanmakuMaskSegment(range = entry.segRange, frames = frames)
    }

    companion object {
        /** 流式读取压缩块，关闭输入流。 */
        fun fromStream(
            input: InputStream,
            type: DanmakuMaskType,
        ): DanmakuMask = input.source().buffer().use { parseFromSource(it, type) }

        /** 从已加载的字节解析压缩块。 */
        fun fromBinary(
            binary: ByteArray,
            type: DanmakuMaskType,
        ): DanmakuMask = parseFromSource(Buffer().write(binary), type)

        private fun parseFromSource(
            source: BufferedSource,
            type: DanmakuMaskType,
        ): DanmakuMask {
            val magic = source.readByteString(4)
            require(magic.utf8() == "MASK") { "Not a mask file" }

            source.skip(8) // version + reserved
            val size = source.readInt()

            val times = LongArray(size)
            val offsets = LongArray(size)
            for (i in 0 until size) {
                times[i] = source.readLong()
                offsets[i] = source.readLong()
            }

            val entries = ArrayList<SegmentEntry>(size)
            var segLastTime = 0L

            for (i in 0 until size) {
                val compressedBytes =
                    if (i == size - 1) {
                        source.readByteArray()
                    } else {
                        source.readByteArray(offsets[i + 1] - offsets[i])
                    }

                val startTime = segLastTime
                val endTime = if (i == size - 1) Long.MAX_VALUE else times[i + 1]
                entries.add(SegmentEntry(startTime until endTime, compressedBytes))
                segLastTime = endTime
            }

            return DanmakuMask(type, entries)
        }
    }
}

enum class DanmakuMaskType {
    WebMask,
    MobMask,
}
