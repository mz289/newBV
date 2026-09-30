package dev.frost819.newbv.biliapi.http.entity.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WebSearchSquareData(
    val trending: Trending,
) {
    @Serializable
    data class Trending(
        val title: String,
        @SerialName("trackid")
        val trackId: String,
        val list: List<Hotword>,
        @SerialName("top_list")
        val topList: JsonElement? = null,
    )
}

/**
 * 热门关键词
 *
 * @param keyword 关键词
 * @param showName 完整关键词
 * @param icon 图标 url
 * @param uri
 * @param goto
 */
@Serializable
data class Hotword(
    val keyword: String,
    @SerialName("show_name")
    val showName: String,
    val icon: String,
    val uri: String,
    val goto: String,
)

@Serializable
data class SearchTendingData(
    @SerialName("trackid")
    val trackId: String,
    val list: List<Hotword>,
    @SerialName("exp_str")
    val expStr: String? = null,
    @SerialName("hotword_egg_info")
    val hotwordEggInfo: Int,
) {
    @Serializable
    data class Hotword(
        val position: Int,
        val keyword: String,
        @SerialName("show_name")
        val showName: String,
        @SerialName("word_type")
        val wordType: Int? = null,
        val icon: String? = null,
        @SerialName("hot_id")
        val hotId: Int,
        @SerialName("is_commercial")
        val isCommercial: Int,
    )
}
