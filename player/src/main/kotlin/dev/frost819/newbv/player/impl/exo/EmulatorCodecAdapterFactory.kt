package dev.frost819.newbv.player.impl.exo

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter

/** Use synchronous queueing for legacy emulator video, preserving other codecs' Media3 defaults. */
@OptIn(UnstableApi::class)
internal class EmulatorCodecAdapterFactory(
    context: Context,
) : MediaCodecAdapter.Factory {
    private val defaultFactory = DefaultMediaCodecAdapterFactory(context)
    private val synchronousFactory = DefaultMediaCodecAdapterFactory(context).forceDisableAsynchronous()

    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        val factory =
            if (
                needsSynchronousEmulatorDecoder(
                    sdk = Build.VERSION.SDK_INT,
                    hardware = Build.HARDWARE,
                    codecName = configuration.codecInfo.name,
                    mimeType = configuration.format.sampleMimeType,
                )
            ) {
                synchronousFactory
            } else {
                defaultFactory
            }
        return factory.createAdapter(configuration)
    }
}

internal fun needsSynchronousEmulatorDecoder(
    sdk: Int,
    hardware: String,
    codecName: String,
    mimeType: String?,
): Boolean =
    sdk >= 31 &&
        (hardware == "ranchu" || hardware == "goldfish") &&
        codecName.startsWith("OMX.google.") &&
        mimeType?.startsWith("video/") == true
