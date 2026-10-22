package com.kuaishou.akdanmaku.ecs.component.action

import android.graphics.Matrix
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionComponentTransformTest {
    @Test
    fun `composed matrix matches platform scale rotate translate including component reuse`() {
        val component = ActionComponent()
        val actual = Matrix()
        for (rotation in listOf(15f, 0f, 90f, -90f, 180f, 270f, -45f, 123.4f, 360f, 15f)) {
            for ((scaleX, scaleY) in listOf(1f to 1f, 2f to 0.5f, -1f to 1.5f, 0f to 0f)) {
                component.rotation = rotation
                component.scale.set(scaleX, scaleY)
                component.position.set(100f, -25f)
                repeat(3) {
                    component.position.x += 1f
                    component.toTransformMatrix(actual)
                    val expected =
                        Matrix().apply {
                            setScale(scaleX, scaleY)
                            postRotate(rotation)
                            postTranslate(component.position.x, component.position.y)
                        }
                    val input = floatArrayOf(0f, 0f, 20f, 0f, 0f, 50f, 100f, 80f)
                    val actualPoints = input.copyOf()
                    val expectedPoints = input.copyOf()
                    actual.mapPoints(actualPoints)
                    expected.mapPoints(expectedPoints)
                    actualPoints.indices.forEach { index ->
                        assertThat(actualPoints[index]).isWithin(0.0001f).of(expectedPoints[index])
                    }
                }
            }
        }
    }
}
