package com.kuaishou.akdanmaku.cache

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi

/** Pack sprites so a dense burst uploads tens of textures instead of thousands. */
@RequiresApi(Build.VERSION_CODES.O)
internal class HardwareBitmapBatch {
    private class Entry(
        val cache: DrawingCache,
        val holder: DrawingCacheHolder,
        val bitmap: Bitmap,
        val source: Rect,
    )

    private val entries = ArrayList<Entry>(BATCH_SIZE)
    private var nextX = 0
    private var nextY = 0
    private var rowHeight = 0
    private var usedWidth = 0

    fun add(cache: DrawingCache) {
        val holder = cache.get() ?: return
        // A configuration change can rebuild a cache before the delayed flush executes.
        if (entries.any { it.cache === cache }) flush()
        val bitmap = holder.bitmap
        if (!bitmap.isMutable) return
        if (bitmap.width > ATLAS_EDGE || bitmap.height > ATLAS_EDGE) {
            holder.prepareForHardwareRendering()
            return
        }
        if (nextX + bitmap.width > ATLAS_EDGE) {
            nextX = 0
            nextY += rowHeight
            rowHeight = 0
        }
        if (nextY + bitmap.height > ATLAS_EDGE) flush()
        cache.increaseReference()
        entries.add(Entry(cache, holder, bitmap, Rect(nextX, nextY, nextX + bitmap.width, nextY + bitmap.height)))
        nextX += bitmap.width
        rowHeight = maxOf(rowHeight, bitmap.height)
        usedWidth = maxOf(usedWidth, nextX)
        if (entries.size == BATCH_SIZE) flush()
    }

    fun flush() {
        if (entries.isEmpty()) return
        var staging: Bitmap? = null
        try {
            val spritePixels = entries.sumOf { it.bitmap.width.toLong() * it.bitmap.height }
            if (usedWidth.toLong() * (nextY + rowHeight) > 2 * spritePixels) {
                // Very different line heights can leave most of a shelf empty.
                entries.forEach { it.holder.prepareForHardwareRendering() }
                return
            }
            staging = Bitmap.createBitmap(usedWidth, nextY + rowHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(staging)
            for (entry in entries) {
                canvas.drawBitmap(entry.bitmap, null, entry.source, null)
            }
            val uploaded = staging.copy(Bitmap.Config.HARDWARE, false) ?: return
            for (entry in entries) {
                if (entry.holder.bitmap === entry.bitmap) entry.holder.useHardwareAtlas(uploaded, entry.source)
            }
        } catch (_: RuntimeException) {
            // The existing CPU cache remains valid if the driver rejects an upload.
        } catch (_: OutOfMemoryError) {
            // Preserve the rendered CPU sprites rather than losing the worker during a burst.
        } finally {
            staging?.recycle()
            for (entry in entries) entry.cache.decreaseReference()
            entries.clear()
            nextX = 0
            nextY = 0
            rowHeight = 0
            usedWidth = 0
        }
    }

    companion object {
        private const val ATLAS_EDGE = 2_048
        private const val BATCH_SIZE = 64
    }
}
