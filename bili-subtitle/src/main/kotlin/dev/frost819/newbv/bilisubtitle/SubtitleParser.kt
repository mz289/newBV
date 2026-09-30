package dev.frost819.newbv.bilisubtitle

import dev.frost819.newbv.bilisubtitle.entity.BiliSubtitle
import dev.frost819.newbv.bilisubtitle.entity.SubtitleItem
import dev.frost819.newbv.bilisubtitle.entity.Timestamp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object SubtitleParser {
    fun fromBccString(bcc: String): List<SubtitleItem> {
        val result = mutableListOf<SubtitleItem>()
        val bccResult =
            runCatching {
                Json.decodeFromString<BiliSubtitle>(bcc)
            }.getOrNull()
        bccResult?.body?.forEach { bccItem ->
            result.add(
                SubtitleItem(
                    from = Timestamp.fromBccString(bccItem.from),
                    to = Timestamp.fromBccString(bccItem.to),
                    content = bccItem.content,
                ),
            )
        }
        return result
    }
}
