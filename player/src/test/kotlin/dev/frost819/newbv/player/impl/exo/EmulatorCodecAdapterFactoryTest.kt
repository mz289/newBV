package dev.frost819.newbv.player.impl.exo

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EmulatorCodecAdapterFactoryTest {
    @Test
    fun `legacy emulator video uses synchronous mode on Android 12 and later`() {
        for (hardware in listOf("ranchu", "goldfish")) {
            assertThat(needsSynchronousEmulatorDecoder(31, hardware, "OMX.google.h264.decoder", "video/avc"))
                .isTrue()
        }
    }

    @Test
    fun `physical devices and modern emulator decoders keep defaults`() {
        assertThat(needsSynchronousEmulatorDecoder(31, "qcom", "OMX.google.h264.decoder", "video/avc"))
            .isFalse()
        assertThat(needsSynchronousEmulatorDecoder(35, "ranchu", "c2.android.avc.decoder", "video/avc"))
            .isFalse()
        assertThat(needsSynchronousEmulatorDecoder(35, "ranchu", "c2.goldfish.avc.decoder", "video/avc"))
            .isFalse()
    }

    @Test
    fun `audio unknown formats and older Android keep defaults`() {
        assertThat(needsSynchronousEmulatorDecoder(31, "ranchu", "OMX.google.aac.decoder", "audio/mp4a-latm"))
            .isFalse()
        assertThat(needsSynchronousEmulatorDecoder(31, "ranchu", "OMX.google.h264.decoder", null))
            .isFalse()
        assertThat(needsSynchronousEmulatorDecoder(30, "ranchu", "OMX.google.h264.decoder", "video/avc"))
            .isFalse()
    }
}
