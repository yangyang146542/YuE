package com.zcode.deepseekbalance

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var keyStore: KeyStore
    private lateinit var widgetPrefs: WidgetPrefs
    private val vm: BalanceViewModel by lazy {
        ViewModelProvider(this).get(BalanceViewModel::class.java)
    }

    /** 当前选中的平台。 */
    private var provider: Provider = Provider.DEEPSEEK

    private lateinit var providerChips: ChipGroup
    private lateinit var keyInputLayout: TextInputLayout
    private lateinit var keyInput: TextInputEditText
    private lateinit var queryButton: MaterialButton
    private lateinit var progress: CircularProgressIndicator
    private lateinit var errorText: TextView
    private lateinit var resultCard: View
    private lateinit var totalLabel: TextView
    private lateinit var totalValue: TextView
    private lateinit var detailContainer: LinearLayout
    private lateinit var availableText: TextView
    private lateinit var usedText: TextView
    private lateinit var historySection: View
    private lateinit var historyChips: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keyStore = KeyStore(this)
        widgetPrefs = WidgetPrefs(this)

        providerChips = findViewById(R.id.providerChips)
        keyInputLayout = findViewById(R.id.keyInputLayout)
        keyInput = findViewById(R.id.keyInput)
        queryButton = findViewById(R.id.queryButton)
        progress = findViewById(R.id.progress)
        errorText = findViewById(R.id.errorText)
        resultCard = findViewById(R.id.resultCard)
        totalLabel = findViewById(R.id.totalLabel)
        totalValue = findViewById(R.id.totalValue)
        detailContainer = findViewById(R.id.detailContainer)
        availableText = findViewById(R.id.availableText)
        usedText = findViewById(R.id.usedText)
        historySection = findViewById(R.id.historySection)
        historyChips = findViewById(R.id.historyChips)

        buildProviderChips()
        applyProvider(widgetPrefs.provider(), resetResult = false)

        findViewById<MaterialButton>(R.id.saveKeyButton).setOnClickListener {
            val k = keyInput.text?.toString().orEmpty().trim()
            if (k.isEmpty()) {
                Snackbar.make(keyInput, R.string.msg_key_empty, Snackbar.LENGTH_SHORT).show()
            } else {
                keyStore.save(provider, k)
                refreshHistory()
                Snackbar.make(keyInput, R.string.msg_key_saved, Snackbar.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.clearKeyButton).setOnClickListener {
            keyStore.clear(provider)
            keyInput.setText("")
            vm.reset()
            Snackbar.make(keyInput, R.string.msg_key_cleared, Snackbar.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.clearHistoryButton).setOnClickListener {
            keyStore.clearHistory(provider)
            refreshHistory()
            Snackbar.make(keyInput, R.string.msg_history_cleared, Snackbar.LENGTH_SHORT).show()
        }

        queryButton.setOnClickListener {
            doQuery(keyInput.text?.toString().orEmpty())
        }

        findViewById<MaterialButton>(R.id.addWidgetButton).setOnClickListener {
            requestPinWidget()
        }

        findViewById<MaterialButton>(R.id.widgetSettingsButton).setOnClickListener {
            startActivity(Intent(this, WidgetSettingsActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }

        refreshHistory()
    }

    // ================= 平台 =================

    private fun buildProviderChips() {
        providerChips.removeAllViews()
        Provider.entries.forEach { p ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = p.displayName
                isCheckable = true
                tag = p.id
            }
            providerChips.addView(chip)
        }
        providerChips.setOnCheckedStateChangeListener { group, _ ->
            val checked = group.findViewById<Chip>(group.checkedChipId) ?: return@setOnCheckedStateChangeListener
            applyProvider(Provider.of(checked.tag as? String), resetResult = true)
        }
    }

    private fun checkProviderChip(p: Provider) {
        for (i in 0 until providerChips.childCount) {
            val chip = providerChips.getChildAt(i) as? Chip ?: continue
            chip.isChecked = (chip.tag as? String) == p.id
        }
    }

    /** 切换平台：各自独立读 Key / 历史，并把结果清掉避免混淆。 */
    private fun applyProvider(p: Provider, resetResult: Boolean) {
        provider = p
        widgetPrefs.setProvider(p)
        checkProviderChip(p)
        keyInputLayout.hint = getString(R.string.hint_api_key_for, p.displayName)
        keyInput.setText(keyStore.load(p))
        vm.reset()
        refreshHistory()
    }

    /** 请求把桌面小部件固定到桌面（由系统弹出确认框）。 */
    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            Snackbar.make(keyInput, R.string.msg_pin_unsupported, Snackbar.LENGTH_LONG).show()
            return
        }
        val provider = ComponentName(this, BalanceWidgetProvider::class.java)
        if (manager.requestPinAppWidget(provider, null, null)) {
            Snackbar.make(keyInput, R.string.msg_pin_requested, Snackbar.LENGTH_LONG).show()
        }
    }

    /** 查询，并把该 Key 记入当前平台的历史。 */
    private fun doQuery(rawKey: String) {
        val key = rawKey.trim()
        if (key.isEmpty()) {
            Snackbar.make(keyInput, R.string.msg_key_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        keyStore.addToHistory(provider, key)
        refreshHistory()
        vm.query(provider, key)
    }

    /** 重绘该平台的历史 API Key 列表；点击即填入并直接查询。 */
    private fun refreshHistory() {
        val keys = keyStore.history(provider)
        historyChips.removeAllViews()

        if (keys.isEmpty()) {
            historySection.visibility = View.GONE
            return
        }
        historySection.visibility = View.VISIBLE

        keys.forEach { key ->
            val chip = Chip(this).apply {
                text = maskKey(key)
                isCheckable = false
                isCloseIconVisible = false
                setOnClickListener {
                    keyInput.setText(key)
                    doQuery(key)
                }
            }
            historyChips.addView(chip)
        }
    }

    /** 列表里不显示完整 Key，只留头尾便于辨认。 */
    private fun maskKey(key: String): String =
        if (key.length <= 10) key.take(3) + "••••"
        else key.take(6) + "••••••" + key.takeLast(4)

    // ================= 渲染 =================

    private fun render(state: UiState) {
        when (state) {
            is UiState.Idle -> {
                progress.visibility = View.GONE
                errorText.visibility = View.GONE
                queryButton.isEnabled = true
            }

            is UiState.Loading -> {
                progress.visibility = View.VISIBLE
                errorText.visibility = View.GONE
                resultCard.visibility = View.GONE
                queryButton.isEnabled = false
            }

            is UiState.Error -> {
                progress.visibility = View.GONE
                queryButton.isEnabled = true
                resultCard.visibility = View.GONE
                errorText.text = state.message
                errorText.visibility = View.VISIBLE
            }

            is UiState.Success -> {
                progress.visibility = View.GONE
                queryButton.isEnabled = true
                errorText.visibility = View.GONE
                bindResult(state.result)
                resultCard.visibility = View.VISIBLE
            }
        }
    }

    private fun symbolFor(currency: String) = when (currency.uppercase()) {
        "CNY" -> "¥"
        "USD" -> "$"
        else -> ""
    }

    private fun bindResult(result: ProviderBalance) {
        val symbol = symbolFor(result.currency)

        // 顺带把结果同步给小部件缓存，保证两边一致
        widgetPrefs.saveResult(
            symbol + result.total,
            result.numericTotal,
            result.currency,
            null
        )

        totalLabel.text = getString(R.string.label_total)
        totalValue.text = symbol + result.total

        // 明细行由平台决定，动态生成
        detailContainer.removeAllViews()
        result.details.forEachIndexed { index, d ->
            val tv = TextView(this).apply {
                text = "${d.label}：${symbol}${d.value}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setTextColor(resolveColorOnContainer())
                if (index > 0) {
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = (8 * resources.displayMetrics.density).toInt()
                    layoutParams = lp
                }
            }
            detailContainer.addView(tv)
        }

        availableText.text = getString(
            if (result.isAvailable) R.string.status_available else R.string.status_unavailable
        )

        val recharge = widgetPrefs.rechargeTotal()
        val amount = result.numericTotal
        if (recharge > 0 && amount != null) {
            val used = recharge - amount
            usedText.text = getString(R.string.label_used) + "：" + symbol +
                String.format(java.util.Locale.US, "%.2f", used)
            usedText.visibility = View.VISIBLE
        } else {
            usedText.visibility = View.GONE
        }
    }

    private fun resolveColorOnContainer(): Int {
        val ta = obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorOnPrimaryContainer))
        val c = ta.getColor(0, 0xFF00105C.toInt())
        ta.recycle()
        return c
    }
}
