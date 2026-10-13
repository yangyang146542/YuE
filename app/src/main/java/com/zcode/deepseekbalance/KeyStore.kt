package com.zcode.deepseekbalance

import android.content.Context
import org.json.JSONArray

/**
 * 用 SharedPreferences 在本机保存各平台的 API Key 与使用历史。
 *
 * **每个平台各自独立存一份 Key 和历史**（切换平台时不会串）。
 * 注意：这里存的是明文（MODE_PRIVATE，仅本应用可读）。若需加密存储，
 * 需要引入 androidx.security-crypto 的 EncryptedSharedPreferences。
 */
class KeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyApi(provider: Provider) = "${provider.id}_api_key"
    private fun keyHistory(provider: Provider) = "${provider.id}_api_key_history"

    fun load(provider: Provider): String = prefs.getString(keyApi(provider), "").orEmpty()

    fun save(provider: Provider, apiKey: String) {
        val k = apiKey.trim()
        prefs.edit().putString(keyApi(provider), k).apply()
        addToHistory(provider, k)
    }

    fun clear(provider: Provider) {
        prefs.edit().remove(keyApi(provider)).apply()
    }

    /** 该平台使用过的 API Key，最近使用的在最前。 */
    fun history(provider: Provider): List<String> {
        val raw = prefs.getString(keyHistory(provider), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotEmpty()) add(s)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToHistory(provider: Provider, apiKey: String) {
        val k = apiKey.trim()
        if (k.isEmpty()) return
        val list = history(provider).toMutableList()
        list.remove(k)
        list.add(0, k)
        while (list.size > MAX_HISTORY) {
            list.removeAt(list.size - 1)
        }
        prefs.edit().putString(keyHistory(provider), JSONArray(list).toString()).apply()
    }

    fun clearHistory(provider: Provider) {
        prefs.edit().remove(keyHistory(provider)).apply()
    }

    private companion object {
        const val PREFS = "deepseek_balance"
        const val MAX_HISTORY = 8
    }
}
