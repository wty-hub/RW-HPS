package net.rwhps.plugin.baiduchatfilter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.rwhps.server.util.log.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 文本审核客户端接口
 *
 * 供 [BaiduChatFilterState] 调用; 测试中可注入假实现。
 */
internal interface TextCensorApi {
    /** 审核一段文本, 返回结论; 请求失败/超时/解析失败返回 null */
    fun checkText(text: String): BaiduCheckResult?

    /** 释放底层资源 */
    fun close()
}

/**
 * 百度内容安全 - 文本审核客户端
 *
 * 文档:
 * - 获取 access_token: https://cloud.baidu.com/doc/Reference/s/9jwvz2egb
 * - 文本审核(内容审核平台): https://cloud.baidu.com/doc/ICR/s/hktjhkpk6
 *
 * access_token 默认有效期约 30 天(2592000 秒), 缓存并在过期前刷新。
 * OkHttpClient 线程安全, 可被多个连接线程并发调用。
 *
 * @author RW-HPS
 */
class BaiduClient(
    private val apiKey: String,
    private val secretKey: String,
    timeoutSeconds: Int,
) : TextCensorApi {
    private var tokenUrl: String = TOKEN_URL
    private var textCensorUrl: String = TEXT_CENSOR_URL

    internal constructor(
        apiKey: String,
        secretKey: String,
        timeoutSeconds: Int,
        tokenUrl: String,
        textCensorUrl: String,
    ) : this(apiKey, secretKey, timeoutSeconds) {
        this.tokenUrl = tokenUrl
        this.textCensorUrl = textCensorUrl
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpireAt: Long = 0L

    /** 缓存 token 并在过期前 1 天刷新 */
    override fun checkText(text: String): BaiduCheckResult? {
        val token = getAccessToken() ?: return null
        val formBody = FormBody.Builder().add("text", text).build()
        val request = Request.Builder()
            .url("$textCensorUrl?access_token=$token")
            .post(formBody)
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.error("[BaiduChatFilter] 文本审核 HTTP ${response.code}: ${response.body?.string()}")
                    return null
                }
                parseCheckResult(response.body?.string() ?: "")
            }
        } catch (e: Exception) {
            Log.error("[BaiduChatFilter] 文本审核请求失败", e)
            null
        }
    }

    private fun getAccessToken(): String? {
        val now = System.currentTimeMillis()
        val cached = accessToken
        if (cached != null && now < tokenExpireAt - TOKEN_REFRESH_AHEAD_MILLIS) {
            return cached
        }
        return synchronized(this) {
            val cachedInner = accessToken
            if (cachedInner != null && now < tokenExpireAt - TOKEN_REFRESH_AHEAD_MILLIS) {
                cachedInner
            } else {
                fetchAccessToken()
            }
        }
    }

    private fun fetchAccessToken(): String? {
        val request = Request.Builder()
            .url("$tokenUrl?grant_type=client_credentials&client_id=$apiKey&client_secret=$secretKey")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.error("[BaiduChatFilter] 获取 access_token 失败 HTTP ${response.code}: $body")
                    return null
                }
                val json = JsonParser.parseString(body).asJsonObject
                val errorCode = json.get("error")?.asString
                if (errorCode != null) {
                    Log.error("[BaiduChatFilter] 获取 access_token 失败: $errorCode ${json.get("error_description")?.asString}")
                    return null
                }
                val token = json.get("access_token")?.asString ?: return null
                val expiresIn = json.get("expires_in")?.asLong ?: DEFAULT_TOKEN_TTL_SECONDS
                accessToken = token
                tokenExpireAt = System.currentTimeMillis() + expiresIn * 1000
                token
            }
        } catch (e: Exception) {
            Log.error("[BaiduChatFilter] 获取 access_token 请求失败", e)
            null
        }
    }

    internal fun parseCheckResult(body: String): BaiduCheckResult? {
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            val errorCode = json.get("error_code")?.asInt
            if (errorCode != null) {
                Log.error("[BaiduChatFilter] 文本审核返回错误: $errorCode ${json.get("error_msg")?.asString}")
                return null
            }
            val conclusionType = json.get("conclusionType")?.asInt
            if (conclusionType == null) {
                Log.error("[BaiduChatFilter] 文本审核响应缺少 conclusionType: $body")
                return null
            }
            val conclusion = json.get("conclusion")?.asString ?: ""
            val hits = parseHits(json.getAsJsonArray("data"))
            BaiduCheckResult(conclusionType, conclusion, hits)
        } catch (e: Exception) {
            Log.error("[BaiduChatFilter] 文本审核响应解析失败: $body", e)
            null
        }
    }

    internal fun parseHits(data: JsonArray?): List<String> {
        if (data == null) {
            return emptyList()
        }
        val hits = mutableListOf<String>()
        for (element in data) {
            val obj = element.asJsonObject
            obj.get("msg")?.asString?.let { hits.add(it) }
            val words = obj.getAsJsonArray("hits")?.firstOrNull()?.asJsonObject?.getAsJsonArray("words")
            if (words != null) {
                for (word in words) {
                    word.asString?.let { hits.add(it) }
                }
            }
        }
        return hits
    }

    override fun close() {
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
    }

    private companion object {
        const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
        const val TEXT_CENSOR_URL = "https://aip.baidubce.com/rest/2.0/solution/v1/text_censor/v2/user_defined"
        const val DEFAULT_TOKEN_TTL_SECONDS = 2592000L
        const val TOKEN_REFRESH_AHEAD_MILLIS = 24 * 60 * 60 * 1000L
    }
}

data class BaiduCheckResult(
    val conclusionType: Int,
    val conclusion: String,
    val hits: List<String>,
) {
    companion object {
        /** 合规 */
        const val TYPE_PASS = 1
        /** 不合规 */
        const val TYPE_BLOCK = 2
        /** 疑似 */
        const val TYPE_SUSPICIOUS = 3
        /** 审核失败 */
        const val TYPE_ERROR = 4
    }
}
