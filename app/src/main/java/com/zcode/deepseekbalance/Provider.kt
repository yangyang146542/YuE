package com.zcode.deepseekbalance

import org.json.JSONObject

/** 余额卡片上的一行明细（标签 + 值）。 */
data class BalanceDetail(val label: String, val value: String)

/** 统一的余额结果，各家响应结构差异在 Provider.parse 里抹平。 */
data class ProviderBalance(
    val isAvailable: Boolean,
    val currency: String,
    val total: String,
    val details: List<BalanceDetail>,
    /** 供「已用金额」计算的数值总额；无法解析时为 null。 */
    val numericTotal: Double?
)

/**
 * 支持「用 API Key 查询余额」的平台。
 *
 * 全部经过官方文档核对 + 真实探测（用一个故意无效的 Key，返回 401 说明端点存在）。
 * 注意：**没有任何平台支持用账号密码查询余额**，因此这里只有 API Key 一种鉴权方式。
 */
enum class Provider(
    val id: String,
    val displayName: String,
    val endpoint: String,
    /** 去哪里申请 Key，用于错误提示。 */
    val keySite: String,
    /** 官方文档地址。 */
    val docs: String
) {
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        endpoint = "https://api.deepseek.com/user/balance",
        keySite = "platform.deepseek.com",
        docs = "https://api-docs.deepseek.com/api/get-user-balance"
    ),
    MOONSHOT(
        id = "moonshot",
        displayName = "Moonshot / Kimi",
        endpoint = "https://api.moonshot.cn/v1/users/me/balance",
        keySite = "platform.moonshot.cn",
        docs = "https://platform.kimi.ai/docs/api/balance"
    ),
    SILICONFLOW(
        id = "siliconflow",
        displayName = "硅基流动",
        endpoint = "https://api.siliconflow.cn/v1/user/info",
        keySite = "cloud.siliconflow.cn",
        docs = "https://docs.siliconflow.com/cn/api-reference/userinfo/get-user-info"
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        endpoint = "https://openrouter.ai/api/v1/key",
        keySite = "openrouter.ai/keys",
        docs = "https://openrouter.ai/docs/api-reference/limits"
    );

    /** 把各家的响应统一成 [ProviderBalance]。schema 均取自官方文档。 */
    fun parse(body: String): ProviderBalance {
        val json = JSONObject(body)
        return when (this) {
            DEEPSEEK -> {
                val info = json.optJSONArray("balance_infos")?.optJSONObject(0)
                val total = info?.optString("total_balance").orEmpty()
                ProviderBalance(
                    isAvailable = json.optBoolean("is_available", false),
                    currency = info?.optString("currency").orEmpty(),
                    total = total,
                    details = listOfNotNull(
                        info?.optString("granted_balance")?.takeIf { it.isNotEmpty() }
                            ?.let { BalanceDetail("赠送余额", it) },
                        info?.optString("topped_up_balance")?.takeIf { it.isNotEmpty() }
                            ?.let { BalanceDetail("充值余额", it) }
                    ),
                    numericTotal = total.toDoubleOrNull()
                )
            }

            MOONSHOT -> {
                val data = json.optJSONObject("data")
                val total = data?.optDouble("available_balance")?.let { trimNum(it) }.orEmpty()
                ProviderBalance(
                    // 这家关键信息在 status 里，HTTP 一律 200
                    isAvailable = json.optBoolean("status", false),
                    currency = if (endpoint.contains(".cn")) "CNY" else "USD",
                    total = total,
                    details = listOfNotNull(
                        data?.optDouble("voucher_balance")?.takeIf { !it.isNaN() }
                            ?.let { BalanceDetail("代金券余额", trimNum(it)) },
                        data?.optDouble("cash_balance")?.takeIf { !it.isNaN() }
                            ?.let { BalanceDetail("现金余额", trimNum(it)) }
                    ),
                    numericTotal = total.toDoubleOrNull()
                )
            }

            SILICONFLOW -> {
                val data = json.optJSONObject("data")
                val total = data?.optString("totalBalance").orEmpty()
                ProviderBalance(
                    isAvailable = json.optBoolean("status", false),
                    currency = "CNY",
                    total = total,
                    details = listOfNotNull(
                        data?.optString("balance")?.takeIf { it.isNotEmpty() }
                            ?.let { BalanceDetail("赠送余额", it) },
                        data?.optString("chargeBalance")?.takeIf { it.isNotEmpty() }
                            ?.let { BalanceDetail("充值余额", it) }
                    ),
                    numericTotal = total.toDoubleOrNull()
                )
            }

            OPENROUTER -> {
                // /api/v1/key 给的是「这个 Key 的额度」，普通 Key 可用；
                // 账户总余额 /api/v1/credits 需要 management key，这里不用。
                val data = json.optJSONObject("data")
                val limit = data?.opt("limit")
                val remaining = data?.opt("limit_remaining")
                val usage = data?.optDouble("usage")?.takeIf { !it.isNaN() }
                val remainingText = when {
                    remaining == null || remaining.toString() == "null" -> "无限制"
                    else -> remaining.toString()
                }
                ProviderBalance(
                    isAvailable = true,
                    currency = "USD",
                    total = remainingText,
                    details = listOfNotNull(
                        usage?.let { BalanceDetail("本 Key 已用", trimNum(it)) },
                        when {
                            limit == null || limit.toString() == "null" -> BalanceDetail("额度上限", "未设置")
                            else -> BalanceDetail("额度上限", limit.toString())
                        }
                    ),
                    numericTotal = remaining?.toString()?.toDoubleOrNull()
                )
            }
        }
    }

    private fun trimNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)

    companion object {
        fun of(id: String?): Provider =
            entries.firstOrNull { it.id == id } ?: DEEPSEEK
    }
}
