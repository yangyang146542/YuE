package com.zcode.deepseekbalance

import java.net.HttpURLConnection
import java.net.URL

/** 查询余额时可能出现的错误，message 已是可直接展示的中文文案。 */
sealed class ApiError(message: String) : Exception(message) {
    class Unauthorized(val site: String) :
        ApiError("API Key 无效或已过期（401）。请到 $site 检查。")
    class InsufficientBalance : ApiError("账户余额不足（402）。")
    class RateLimited : ApiError("请求过于频繁，已被限流（429），请稍后重试。")
    class ServerError(val code: Int, val body: String) :
        ApiError("服务端返回错误 $code" + if (body.isNotBlank()) "：$body" else "")
    class Parse(val cause2: Throwable) :
        ApiError("返回内容无法解析：${cause2.message ?: cause2.javaClass.simpleName}")
    class Network(val cause2: Throwable) :
        ApiError("网络连接失败：${cause2.message ?: cause2.javaClass.simpleName}")
}

/** 统一的余额查询客户端：按 [Provider] 选端点，用 Bearer API Key 鉴权。 */
object BalanceApi {

    fun fetch(provider: Provider, apiKey: String): ProviderBalance {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(provider.endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 20_000
            }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            when (code) {
                401, 403 -> throw ApiError.Unauthorized(provider.keySite)
                402 -> throw ApiError.InsufficientBalance()
                429 -> throw ApiError.RateLimited()
            }
            if (code !in 200..299) throw ApiError.ServerError(code, body.trim())

            return try {
                provider.parse(body)
            } catch (e: Exception) {
                throw ApiError.Parse(e)
            }
        } catch (e: ApiError) {
            throw e
        } catch (e: Exception) {
            throw ApiError.Network(e)
        } finally {
            conn?.disconnect()
        }
    }
}
