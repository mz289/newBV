package dev.frost819.newbv.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Cookie 登录凭证解析器。
 *
 * 支持三种输入（参考 NeoBV 的 Cookie 导入 + 浏览器 Cookie 粘贴）：
 * 1. 浏览器原始 Cookie 串：`SESSDATA=xxx; bili_jct=yyy; DedeUserID=123; ...`
 *    （分隔符支持分号或换行，值保持原样不转码）
 * 2. newBV 导出的凭证 JSON：`{"uid":...,"sessData":...,"biliJct":...}`（[AuthData] 序列化格式）
 * 3. NeoBV 导出的凭证 JSON：`{"DedeUserID":...,"SESSDATA":...,"bili_jct":...}`（Cookie 名作键）
 *
 * 必需字段：SESSDATA、bili_jct、DedeUserID（uid）；其余字段缺失时用空串兜底。
 */
object CookieLoginParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Cookie 名 → AuthData 字段的容错读取配置。 */
    private data class CookieFields(
        val uid: Long,
        val uidCkMd5: String,
        val sid: String,
        val biliJct: String,
        val sessData: String,
    )

    /**
     * 解析 Cookie 登录凭证。
     *
     * @param input Cookie 串或凭证 JSON。
     * @return 解析出的 [AuthData]（accessToken/refreshToken 为空，仅 Web 通道可用）。
     * @throws IllegalArgumentException 输入为空、JSON 无效或缺少必需字段时抛出，消息可直接展示。
     */
    fun parse(input: String): AuthData {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("内容为空，请粘贴 Cookie 或导入凭证文件")

        return if (trimmed.startsWith("{")) {
            parseJson(trimmed)
        } else {
            parseCookieString(trimmed)
        }
    }

    /** 解析 JSON 凭证：优先 newBV [AuthData] 格式，失败后按 Cookie 名键（NeoBV 格式）提取。 */
    private fun parseJson(text: String): AuthData =
        runCatching { AuthData.fromJson(text) }.getOrElse {
            val fields =
                runCatching {
                    val obj = json.parseToJsonElement(text).jsonObject

                    fun str(vararg keys: String): String =
                        keys.firstNotNullOfOrNull { key -> obj[key] }?.let {
                            runCatching { it.jsonPrimitive.content }.getOrNull()
                        } ?: ""

                    fun long(vararg keys: String): Long =
                        keys.firstNotNullOfOrNull { key -> obj[key] }?.let {
                            runCatching { it.jsonPrimitive.longOrNull }.getOrNull()
                        } ?: 0L

                    CookieFields(
                        uid = long("DedeUserID", "uid"),
                        uidCkMd5 = str("DedeUserID__ckMd5", "uidCkMd5"),
                        sid = str("sid"),
                        biliJct = str("bili_jct", "biliJct"),
                        sessData = str("SESSDATA", "sessData"),
                    )
                }.getOrElse { throw IllegalArgumentException("JSON 格式无效，请粘贴 Cookie 串或凭证 JSON") }
            fields.toAuthData()
        }

    /** 解析浏览器 Cookie 串（分号或换行分隔的 name=value 对，值不转码）。 */
    private fun parseCookieString(text: String): AuthData {
        val pairs = mutableMapOf<String, String>()
        for (line in text.split('\n')) {
            for (part in line.split(';')) {
                val pair = part.trim()
                if (pair.isEmpty()) continue
                val name = pair.substringBefore('=', "").trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isNotEmpty()) pairs[name] = value
            }
        }

        return CookieFields(
            uid = pairs["DedeUserID"]?.toLongOrNull() ?: 0L,
            uidCkMd5 = pairs["DedeUserID__ckMd5"] ?: "",
            sid = pairs["sid"] ?: "",
            biliJct = pairs["bili_jct"] ?: "",
            sessData = pairs["SESSDATA"] ?: "",
        ).toAuthData()
    }

    /** 校验必需字段并构造 [AuthData]。Cookie 登录无 access_token，仅 Web 通道可用。 */
    private fun CookieFields.toAuthData(): AuthData {
        if (uid == 0L) throw IllegalArgumentException("Cookie 缺少 DedeUserID（用户 ID）")
        if (sessData.isBlank()) throw IllegalArgumentException("Cookie 缺少 SESSDATA")
        if (biliJct.isBlank()) throw IllegalArgumentException("Cookie 缺少 bili_jct")
        return AuthData(
            uid = uid,
            uidCkMd5 = uidCkMd5,
            sid = sid,
            biliJct = biliJct,
            sessData = sessData,
            tokenExpiredDate = 0L,
            accessToken = "",
            refreshToken = "",
        )
    }
}
