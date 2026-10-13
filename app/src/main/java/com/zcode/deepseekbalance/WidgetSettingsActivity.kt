package com.zcode.deepseekbalance

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.R as MaterialR

/**
 * 小部件设置页。
 *
 * 两个入口：
 * 1. App 主活动里的「小部件设置」按钮（常规设置）
 * 2. 添加小部件时桌面自动拉起（配置模式，用 intent action 是否为 APPWIDGET_CONFIGURE 判断）
 *
 * 外观设置是全局的（小部件上已无设置按钮），配置模式下仍然需要返回 widgetId 让桌面绑定部件。
 */
class WidgetSettingsActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isConfig = false

    private lateinit var keyStore: KeyStore
    private lateinit var prefs: WidgetPrefs

    private lateinit var keyInput: TextInputEditText
    private lateinit var intervalChips: ChipGroup
    private lateinit var styleChips: ChipGroup
    private lateinit var colorTargetChips: ChipGroup
    private lateinit var colorSourceChips: ChipGroup
    private lateinit var colorPreview: View
    private lateinit var colorPreviewText: android.widget.TextView
    private lateinit var roleChips: ChipGroup
    private lateinit var colorGrid: ColorGridView
    private lateinit var alphaRow: View
    private lateinit var alphaSlider: Slider
    private lateinit var alphaValue: android.widget.TextView
    private lateinit var hexRow: View
    private lateinit var hexInput: TextInputEditText
    private lateinit var cornerSlider: Slider
    private lateinit var cornerLabel: android.widget.TextView
    private lateinit var fontChips: ChipGroup
    private lateinit var rechargeInput: TextInputEditText

    private lateinit var carouselSwitch: MaterialSwitch
    private lateinit var folderLabel: TextView
    private lateinit var scaleChips: ChipGroup
    private lateinit var boxRightInput: TextInputEditText
    private lateinit var boxTopInput: TextInputEditText
    private lateinit var boxWInput: TextInputEditText
    private lateinit var boxHInput: TextInputEditText
    private lateinit var boxBorderInput: TextInputEditText
    private lateinit var boxCornerInput: TextInputEditText
    private lateinit var multiplierPicker: android.widget.NumberPicker
    private lateinit var intervalEstimate: TextView
    private lateinit var providerChips: ChipGroup
    private lateinit var keyLayout: com.google.android.material.textfield.TextInputLayout

    /** 当前平台（全局设置）。 */
    private var provider: Provider = Provider.DEEPSEEK

    /** 系统文件夹选择器（SAF）。 */
    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // 某些提供方不支持持久化，忽略即可
            }
            prefs.setFolderUri(uri.toString())
            updateFolderLabel()
        }
    }

    /** target -> [source, role, argb] */
    private val colorState = HashMap<String, IntArray>()

    private var activeTarget: String = WidgetPrefs.TARGETS[0]

    private val intervalValues = intArrayOf(0, 15, 30, 60, 180, 360, 720, 1440)
    private val intervalLabels = intArrayOf(
        R.string.interval_off, R.string.interval_15m, R.string.interval_30m, R.string.interval_1h,
        R.string.interval_3h, R.string.interval_6h, R.string.interval_12h, R.string.interval_24h
    )
    private val styleValues = intArrayOf(
        WidgetPrefs.STYLE_FROSTED, WidgetPrefs.STYLE_TRANSPARENT, WidgetPrefs.STYLE_SOLID
    )
    private val styleLabels = intArrayOf(
        R.string.style_frosted, R.string.style_transparent, R.string.style_solid
    )
    private val targetLabels = intArrayOf(
        R.string.target_bg, R.string.target_text, R.string.target_icon, R.string.target_border
    )
    private val sourceValues = intArrayOf(
        WidgetPrefs.COLOR_ROLE, WidgetPrefs.COLOR_PALETTE, WidgetPrefs.COLOR_HEX
    )
    private val sourceLabels = intArrayOf(R.string.source_role, R.string.source_palette, R.string.source_hex)
    private val fontLabels = intArrayOf(
        R.string.font_small, R.string.font_default, R.string.font_large, R.string.font_xlarge
    )
    private val scaleValues = intArrayOf(
        WidgetPrefs.SCALE_CROP, WidgetPrefs.SCALE_STRETCH, WidgetPrefs.SCALE_FIT
    )
    private val scaleLabels = intArrayOf(
        R.string.scale_crop, R.string.scale_stretch, R.string.scale_fit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_settings)

        keyStore = KeyStore(this)
        prefs = WidgetPrefs(this)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        isConfig = intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE

        bindViews()
        provider = prefs.provider()
        buildProviderChips()
        keyLayout.hint = getString(R.string.hint_api_key_for, provider.displayName)
        keyInput.setText(keyStore.load(provider))
        rechargeInput.setText(
            prefs.rechargeTotal().takeIf { it > 0 }?.let { trimNumber(it) } ?: ""
        )

        for (target in WidgetPrefs.TARGETS) {
            colorState[target] = intArrayOf(
                prefs.colorSource(target),
                prefs.colorRole(target),
                prefs.colorCustom(target)
            )
        }

        buildChips(intervalChips, intervalLabels, intervalValues, prefs.intervalMinutes())
        buildChips(styleChips, styleLabels, styleValues, prefs.style())
        buildTargetChips()
        buildChips(colorSourceChips, sourceLabels, sourceValues, colorState[activeTarget]!![0])
        buildRoleChips()
        buildFontChips(prefs.fontScale())

        buildChips(scaleChips, scaleLabels, scaleValues, prefs.scaleMode())
        loadCarouselFields()
        carouselSwitch.isChecked = prefs.carouselEnabled()
        carouselSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.setCarouselEnabled(checked)
        }
        findViewById<MaterialButton>(R.id.pickFolderButton).setOnClickListener {
            pickFolder.launch(null)
        }
        updateFolderLabel()

        multiplierPicker.minValue = 1
        multiplierPicker.maxValue = WidgetPrefs.MAX_MULTIPLIER
        multiplierPicker.value = prefs.carouselMultiplier()
        multiplierPicker.setOnValueChangedListener { _, _, _ ->
            prefs.setCarouselMultiplier(multiplierPicker.value)
            updateIntervalEstimate()
        }
        updateIntervalEstimate()

        cornerSlider.value = prefs.cornerRadiusDp().coerceIn(0f, 48f)
        updateCornerLabel()

        colorSourceChips.setOnCheckedStateChangeListener { _, _ -> onUiChanged() }
        colorTargetChips.setOnCheckedStateChangeListener { _, _ -> onTargetChanged() }
        roleChips.setOnCheckedStateChangeListener { _, _ ->
            colorState[activeTarget]!![1] = selectedValue(roleChips, colorState[activeTarget]!![1])
            refreshPreview()
        }
        colorGrid.onColorPicked = { rgb ->
            val alpha = Color.alpha(colorState[activeTarget]!![2])
            colorState[activeTarget]!![2] = (alpha shl 24) or (rgb and 0x00FFFFFF)
            refreshPreview()
        }
        alphaSlider.addOnChangeListener { _, value, _ ->
            val alpha = value.toInt().coerceIn(0, 255)
            val current = colorState[activeTarget]!![2]
            colorState[activeTarget]!![2] = (alpha shl 24) or (current and 0x00FFFFFF)
            alphaValue.text = alpha.toString()
            refreshPreview()
        }
        cornerSlider.addOnChangeListener { _, _, _ -> updateCornerLabel() }
        findViewById<MaterialButton>(R.id.hexApplyButton).setOnClickListener { applyHex() }
        findViewById<MaterialButton>(R.id.settingsSaveButton).setOnClickListener { save() }

        val cancelButton = findViewById<MaterialButton>(R.id.settingsCancelButton)
        if (isConfig) {
            cancelButton.visibility = View.VISIBLE
            cancelButton.setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        onUiChanged()
    }

    private fun bindViews() {
        keyInput = findViewById(R.id.settingsKeyInput)
        intervalChips = findViewById(R.id.intervalChips)
        styleChips = findViewById(R.id.styleChips)
        colorTargetChips = findViewById(R.id.colorTargetChips)
        colorSourceChips = findViewById(R.id.colorSourceChips)
        colorPreview = findViewById(R.id.colorPreview)
        colorPreviewText = findViewById(R.id.colorPreviewText)
        roleChips = findViewById(R.id.roleChips)
        colorGrid = findViewById(R.id.colorGrid)
        alphaRow = findViewById(R.id.alphaRow)
        alphaSlider = findViewById(R.id.alphaSlider)
        alphaValue = findViewById(R.id.alphaValue)
        hexRow = findViewById(R.id.hexRow)
        hexInput = findViewById(R.id.hexInput)
        cornerSlider = findViewById(R.id.cornerSlider)
        cornerLabel = findViewById(R.id.cornerLabel)
        fontChips = findViewById(R.id.fontChips)
        rechargeInput = findViewById(R.id.rechargeInput)
        carouselSwitch = findViewById(R.id.carouselSwitch)
        folderLabel = findViewById(R.id.folderLabel)
        scaleChips = findViewById(R.id.scaleChips)
        boxRightInput = findViewById(R.id.boxRightInput)
        boxTopInput = findViewById(R.id.boxTopInput)
        boxWInput = findViewById(R.id.boxWInput)
        boxHInput = findViewById(R.id.boxHInput)
        boxBorderInput = findViewById(R.id.boxBorderInput)
        boxCornerInput = findViewById(R.id.boxCornerInput)
        multiplierPicker = findViewById(R.id.multiplierPicker)
        intervalEstimate = findViewById(R.id.intervalEstimate)
        providerChips = findViewById(R.id.settingsProviderChips)
        keyLayout = findViewById(R.id.settingsKeyLayout)
    }

    private fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val sb = StringBuilder()
        if (h > 0) sb.append(h).append(" 小时 ")
        if (m > 0) sb.append(m).append(" 分 ")
        if (s > 0 || sb.isEmpty()) sb.append(s).append(" 秒")
        return sb.toString().trim()
    }

    private fun updateIntervalEstimate() {
        // 自动轮播与「点图片手动切换」是并存的，不再有二选一
        intervalEstimate.text = getString(
            R.string.interval_estimate,
            formatDuration(multiplierPicker.value * WidgetPrefs.BASE_ADVANCE_SECONDS)
        )
    }

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
            if (p.id == provider.id) chip.isChecked = true
        }
        providerChips.setOnCheckedStateChangeListener { group, _ ->
            val checked = group.findViewById<Chip>(group.checkedChipId)
                ?: return@setOnCheckedStateChangeListener
            provider = Provider.of(checked.tag as? String)
            prefs.setProvider(provider)
            keyLayout.hint = getString(R.string.hint_api_key_for, provider.displayName)
            keyInput.setText(keyStore.load(provider))
        }
    }

    // ================= 图片轮播 =================

    private fun loadCarouselFields() {
        boxRightInput.setText(trimNumber(prefs.boxRightDp().toDouble()))
        boxTopInput.setText(trimNumber(prefs.boxTopDp().toDouble()))
        boxWInput.setText(trimNumber(prefs.boxWidthDp().toDouble()))
        boxHInput.setText(trimNumber(prefs.boxHeightDp().toDouble()))
        boxBorderInput.setText(trimNumber(prefs.boxBorderDp().toDouble()))
        boxCornerInput.setText(trimNumber(prefs.boxCornerDp().toDouble()))
    }

    private fun updateFolderLabel() {
        val uri = prefs.folderUri()
        if (uri.isNullOrEmpty()) {
            folderLabel.text = getString(R.string.folder_none)
        } else {
            val count = ImageRepository.listImages(this).size
            val name = Uri.parse(uri).lastPathSegment?.substringAfterLast(':') ?: uri
            folderLabel.text = getString(R.string.folder_selected, name, count)
        }
    }

    private fun readDp(input: TextInputEditText, fallback: Float): Float =
        input.text?.toString()?.trim()?.toFloatOrNull()?.takeIf { it >= 0f } ?: fallback

    // ================= chips 构建 =================

    private fun buildChips(group: ChipGroup, labels: IntArray, values: IntArray, selected: Int) {
        group.removeAllViews()
        labels.forEachIndexed { index, labelRes ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(labelRes)
                isCheckable = true
                tag = values[index]
            }
            group.addView(chip)
            if (values[index] == selected) chip.isChecked = true
        }
    }

    private fun buildTargetChips() {
        colorTargetChips.removeAllViews()
        WidgetPrefs.TARGETS.forEachIndexed { index, target ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(targetLabels[index])
                isCheckable = true
                tag = target
                chipIcon = getDrawable(R.drawable.ic_circle)
                chipIconTint = ColorStateList.valueOf(resolveCurrent(target))
            }
            colorTargetChips.addView(chip)
            if (target == activeTarget) chip.isChecked = true
        }
    }

    private fun buildRoleChips() {
        roleChips.removeAllViews()
        MATERIAL_ROLE_LABELS.forEachIndexed { index, labelRes ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(labelRes)
                isCheckable = true
                tag = index
                chipIcon = getDrawable(R.drawable.ic_circle)
                chipIconTint = ColorStateList.valueOf(resolveRole(index))
            }
            roleChips.addView(chip)
            if (index == colorState[activeTarget]!![1]) chip.isChecked = true
        }
    }

    private fun buildFontChips(currentScale: Float) {
        fontChips.removeAllViews()
        val currentIndex = WidgetPrefs.FONT_SCALES
            .indexOfFirst { kotlin.math.abs(it - currentScale) < 0.01f }
            .let { if (it < 0) 1 else it }
        fontLabels.forEachIndexed { index, labelRes ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(labelRes)
                isCheckable = true
                tag = index
            }
            fontChips.addView(chip)
            if (index == currentIndex) chip.isChecked = true
        }
    }

    // ================= 颜色 =================

    private fun resolveCurrent(target: String): Int {
        val state = colorState[target] ?: return WidgetPrefs.DEFAULT_CUSTOM
        return if (state[0] == WidgetPrefs.COLOR_ROLE) resolveRole(state[1]) else state[2]
    }

    private fun resolveRole(roleIndex: Int): Int = resolveRoleColor(this, roleIndex)

    private fun onTargetChanged() {
        val selected = selectedTarget()
        if (selected != activeTarget) {
            activeTarget = selected
            rebuildSourceChips(colorState[activeTarget]!![0])
            roleChips.removeAllViews()
            buildRoleChips()
        }
        onUiChanged()
    }

    private fun rebuildSourceChips(selected: Int) {
        colorSourceChips.removeAllViews()
        sourceLabels.forEachIndexed { index, labelRes ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(labelRes)
                isCheckable = true
                tag = sourceValues[index]
            }
            colorSourceChips.addView(chip)
            if (sourceValues[index] == selected) chip.isChecked = true
        }
    }

    private fun onUiChanged() {
        val state = colorState[activeTarget]!!
        val source = selectedValue(colorSourceChips, state[0])
        state[0] = source

        roleChips.visibility = if (source == WidgetPrefs.COLOR_ROLE) View.VISIBLE else View.GONE
        colorGrid.visibility = if (source == WidgetPrefs.COLOR_PALETTE) View.VISIBLE else View.GONE
        hexRow.visibility = if (source == WidgetPrefs.COLOR_HEX) View.VISIBLE else View.GONE
        alphaRow.visibility = if (source == WidgetPrefs.COLOR_ROLE) View.GONE else View.VISIBLE

        if (source == WidgetPrefs.COLOR_PALETTE) {
            colorGrid.selectColor(state[2])
        }
        if (source == WidgetPrefs.COLOR_HEX) {
            hexInput.setText(String.format("#%08X", state[2]))
        }
        alphaSlider.value = Color.alpha(state[2]).toFloat().coerceIn(0f, 255f)
        alphaValue.text = Color.alpha(state[2]).toString()

        for (i in 0 until colorTargetChips.childCount) {
            val chip = colorTargetChips.getChildAt(i) as Chip
            val target = chip.tag as String
            chip.chipIconTint = ColorStateList.valueOf(resolveCurrent(target))
        }
        refreshPreview()
    }

    private fun refreshPreview() {
        val color = resolveCurrent(activeTarget)
        colorPreview.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((1 * resources.displayMetrics.density).toInt(), 0x33808080)
        }
        colorPreviewText.text = String.format("#%08X  ·  a=%d", color, Color.alpha(color))
    }

    private fun applyHex() {
        val text = hexInput.text?.toString().orEmpty().trim().removePrefix("#")
        val parsed = when (text.length) {
            6 -> text.toLongOrNull(16)?.let { (0xFF shl 24) or it.toInt() }
            8 -> text.toLongOrNull(16)?.toInt()
            else -> null
        }
        if (parsed == null) {
            Snackbar.make(hexInput, R.string.msg_invalid_hex, Snackbar.LENGTH_LONG).show()
            return
        }
        colorState[activeTarget]!![2] = parsed
        alphaSlider.value = Color.alpha(parsed).toFloat().coerceIn(0f, 255f)
        alphaValue.text = Color.alpha(parsed).toString()
        refreshPreview()
    }

    // ================= 保存 =================

    private fun updateCornerLabel() {
        cornerLabel.text = getString(R.string.settings_corner_label) +
            "  ·  " + cornerSlider.value.toInt() + "dp"
    }

    private fun selectedValue(group: ChipGroup, fallback: Int): Int {
        val checkedId = group.checkedChipId
        if (checkedId == View.NO_ID) return fallback
        val chip = group.findViewById<Chip>(checkedId) ?: return fallback
        return (chip.tag as? Int) ?: fallback
    }

    private fun selectedTarget(): String {
        val checkedId = colorTargetChips.checkedChipId
        if (checkedId == View.NO_ID) return activeTarget
        val chip = colorTargetChips.findViewById<Chip>(checkedId) ?: return activeTarget
        return (chip.tag as? String) ?: activeTarget
    }

    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun save() {
        keyStore.save(provider, keyInput.text?.toString().orEmpty())
        prefs.setIntervalMinutes(selectedValue(intervalChips, prefs.intervalMinutes()))
        prefs.setRechargeTotal(
            rechargeInput.text?.toString().orEmpty().trim().toDoubleOrNull() ?: 0.0
        )

        // 外观设置全局生效
        prefs.setStyle(selectedValue(styleChips, prefs.style()))
        prefs.setCornerRadiusDp(cornerSlider.value)
        val fontIndex = selectedValue(fontChips, 1).coerceIn(0, WidgetPrefs.FONT_SCALES.size - 1)
        prefs.setFontScale(WidgetPrefs.FONT_SCALES[fontIndex])

        for (target in WidgetPrefs.TARGETS) {
            val state = colorState[target]!!
            prefs.setColorSource(target, state[0])
            prefs.setColorRole(target, state[1])
            prefs.setColorCustom(target, state[2])
        }

        // 图片轮播
        prefs.setCarouselEnabled(carouselSwitch.isChecked)
        prefs.setBoxRightDp(readDp(boxRightInput, prefs.boxRightDp()))
        prefs.setBoxTopDp(readDp(boxTopInput, prefs.boxTopDp()))
        prefs.setBoxWidthDp(readDp(boxWInput, prefs.boxWidthDp()))
        prefs.setBoxHeightDp(readDp(boxHInput, prefs.boxHeightDp()))
        prefs.setBoxBorderDp(readDp(boxBorderInput, prefs.boxBorderDp()))
        prefs.setBoxCornerDp(readDp(boxCornerInput, prefs.boxCornerDp()))
        prefs.setScaleMode(selectedValue(scaleChips, prefs.scaleMode()))
        prefs.setCarouselMultiplier(multiplierPicker.value)

        WidgetScheduler.schedule(this)
        WidgetRenderer.renderAll(this)
        sendBroadcast(
            Intent(this, BalanceWidgetProvider::class.java)
                .setAction(BalanceWidgetProvider.ACTION_REFRESH)
        )

        // 让轮播重新读取图片列表
        val widgetManager = AppWidgetManager.getInstance(this)
        widgetManager.notifyAppWidgetViewDataChanged(
            widgetManager.getAppWidgetIds(ComponentName(this, BalanceWidgetProvider::class.java)),
            R.id.carousel
        )

        if (isConfig) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        } else {
            Snackbar.make(keyInput, R.string.settings_saved, Snackbar.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private val MATERIAL_ROLES = intArrayOf(
            MaterialR.attr.colorPrimary,
            MaterialR.attr.colorOnPrimary,
            MaterialR.attr.colorPrimaryContainer,
            MaterialR.attr.colorOnPrimaryContainer,
            MaterialR.attr.colorSecondary,
            MaterialR.attr.colorSecondaryContainer,
            MaterialR.attr.colorTertiary,
            MaterialR.attr.colorTertiaryContainer,
            MaterialR.attr.colorError,
            MaterialR.attr.colorSurface,
            MaterialR.attr.colorSurfaceVariant,
            MaterialR.attr.colorOutline,
            MaterialR.attr.colorOnSurface,
            MaterialR.attr.colorOnSurfaceVariant
        )

        private val MATERIAL_ROLE_LABELS = intArrayOf(
            R.string.role_primary,
            R.string.role_on_primary,
            R.string.role_primary_container,
            R.string.role_on_primary_container,
            R.string.role_secondary,
            R.string.role_secondary_container,
            R.string.role_tertiary,
            R.string.role_tertiary_container,
            R.string.role_error,
            R.string.role_surface,
            R.string.role_surface_variant,
            R.string.role_outline,
            R.string.role_on_surface,
            R.string.role_on_surface_variant
        )

        /** 供渲染层复用：按角色下标解析 Material 颜色。 */
        fun resolveRoleColor(context: android.content.Context, roleIndex: Int): Int {
            val attr = MATERIAL_ROLES.getOrElse(roleIndex) { MaterialR.attr.colorPrimary }
            return try {
                val themed = DynamicColors.wrapContextIfAvailable(context)
                val array = themed.obtainStyledAttributes(intArrayOf(attr))
                val color = array.getColor(0, Color.GRAY)
                array.recycle()
                color
            } catch (e: Exception) {
                Color.GRAY
            }
        }
    }
}
