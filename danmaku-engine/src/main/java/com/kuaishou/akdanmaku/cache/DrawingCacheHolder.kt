/*
 * The MIT License (MIT)
 *
 * Copyright 2021 Kwai, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.kuaishou.akdanmaku.cache

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import com.kuaishou.akdanmaku.ext.EMPTY_BITMAP
import java.lang.Exception
import kotlin.math.max

/**
 *
 *
 * @author Xana
 * @since 2021-06-22
 */
class DrawingCacheHolder {
    internal class Image(
        val bitmap: Bitmap,
        val source: Rect? = null,
    ) {
        val hardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
    }

    @Volatile
    internal var image = Image(EMPTY_BITMAP)
        private set

    private var bitmapDensity = Bitmap.DENSITY_NONE

    var canvas: Canvas = Canvas()
    var bitmap: Bitmap
        get() = image.bitmap
        set(value) {
            bitmapDensity = value.density
            image = Image(value)
        }
    var width: Int = 0
    var height: Int = 0

    fun buildCache(
        w: Int,
        h: Int,
        density: Int,
        checkSize: Boolean,
        bitsPerPixel: Int = 32,
    ) {
        val reuse = if (checkSize) (w == width && h == height) else w <= width && h <= height
        if (bitmap != EMPTY_BITMAP && !bitmap.isRecycled) {
            if (reuse && bitmap.isMutable) {
                bitmap.eraseColor(Color.TRANSPARENT)
                canvas.setBitmap(bitmap)
                return
            }
        }
        width = max(1, w)
        height = max(1, h)
        @Suppress("DEPRECATION")
        val config = if (bitsPerPixel == 32) Bitmap.Config.ARGB_8888 else Bitmap.Config.ARGB_4444
        try {
            bitmap =
                Bitmap.createBitmap(width, height, config).also {
                    if (density > 0) {
                        it.density = density
                    }
                    canvas.setBitmap(it)
                    canvas.density = density
                }
        } catch (e: Exception) {
            bitmap = EMPTY_BITMAP
            canvas.setBitmap(null)
            width = 0
            height = 0
        }
    }

    fun erase() {
        if (bitmap.isRecycled) return
        if (bitmap.isMutable) {
            bitmap.eraseColor(Color.TRANSPARENT)
        } else {
            // A GPU cache is immutable. Recycled pool entries need a fresh writable staging bitmap.
            val density = bitmapDensity
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { this.density = density }
            canvas.setBitmap(bitmap)
        }
    }

    /** Upload once on the cache worker and release the CPU staging canvas. */
    internal fun prepareForHardwareRendering(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bitmap == EMPTY_BITMAP || bitmap.isRecycled) return false
        if (bitmap.config == Bitmap.Config.HARDWARE) return true
        return try {
            val uploaded = bitmap.copy(Bitmap.Config.HARDWARE, false) ?: return false
            canvas.setBitmap(null)
            bitmap = uploaded
            true
        } catch (_: RuntimeException) {
            false
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    internal fun useHardwareAtlas(
        atlas: Bitmap,
        source: Rect,
    ) {
        canvas.setBitmap(null)
        image = Image(atlas, source)
    }

    fun recycle() {
        if (bitmap == EMPTY_BITMAP) return
        canvas.setBitmap(null)
        bitmap = EMPTY_BITMAP
        width = 0
        height = 0
    }
}
