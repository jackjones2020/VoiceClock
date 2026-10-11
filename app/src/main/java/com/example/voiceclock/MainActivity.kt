package com.example.voiceclock

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var mode: Spinner
    private lateinit var interval: Spinner
    private lateinit var slotList: LinearLayout
    private val days = mutableListOf<CheckBox>()
    private val slots = mutableListOf<TimeSlot>()
    private val intervals = listOf(1, 5, 10, 15, 30, 60)
    private var tts: TextToSpeech? = null
    private var speechReady = false
    private var speechInitialized = false
    private var previewPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = Settings.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text; textSize = size; setPadding(0, dp(14), 0, dp(8)); root.addView(this)
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text; setOnClickListener { action() }; root.addView(this)
        }
        label("语音报时", 28f)
        label("试听会立即播报。开启后仅在选定星期和时段内报时；设置会保存在手机中。")
        button("试听当前时间") {
            previewPending = true
            if (speechReady) preview()
            else if (speechInitialized) { previewPending = false; status.text = "中文语音不可用，请检查系统文字转语音设置" }
            else status.text = "正在准备中文语音…"
        }
        label("报时模式", 20f)
        mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Schedule.modes)
            setSelection(saved.mode)
        }
        root.addView(mode)
        interval = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, intervals.map { "每 $it 分钟" })
            setSelection(intervals.indexOf(saved.interval).coerceAtLeast(0))
        }
        root.addView(interval)
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { interval.visibility = if (position == 0) View.VISIBLE else View.GONE }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        label("每周哪些天", 20f)
        val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        names.chunked(4).forEachIndexed { rowIndex, group ->
            val row = LinearLayout(this)
            group.forEachIndexed { i, name ->
                val day = rowIndex * 4 + i + 1
                val check = CheckBox(this).apply { text = name; isChecked = day in saved.days }
                days.add(check); row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            }
            root.addView(row)
        }
        label("每天的报时时段", 20f)
        label("包含开始时间，不包含结束时间。开始和结束相同表示全天；结束早于开始表示跨午夜，凌晨部分属于前一天。", 14f)
        slots.addAll(saved.slots)
        slotList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(slotList)
        renderSlots()
        button("＋ 添加时段") { slots.add(TimeSlot(8 * 60, 22 * 60)); renderSlots() }
        button("保存设置") {
            val schedule = readSchedule() ?: return@button
            Settings.save(this, schedule)
            if (Settings.enabled(this)) startService(Intent(this, ClockService::class.java).setAction(ClockService.RELOAD))
            showStatus("设置已保存", schedule)
        }
        button("保存并开启语音报时") {
            val schedule = readSchedule() ?: return@button
            Settings.save(this, schedule); Settings.setEnabled(this, true)
            startForegroundService(Intent(this, ClockService::class.java).setAction(ClockService.START))
            showStatus(if (schedule.allows(ZonedDateTime.now())) "已开启，将立即报时" else "已开启，当前不在报时时段", schedule)
        }
        button("停止报时") {
            Settings.setEnabled(this, false); stopService(Intent(this, ClockService::class.java)); status.text = "已停止，设置已保留"
        }
        status = TextView(this).apply { textSize = 15f; setPadding(0, dp(16), 0, dp(16)) }
        root.addView(status)
        showStatus(if (Settings.enabled(this)) "报时已开启" else "当前未开启", saved)
        setContentView(scroll)
        tts = TextToSpeech(this, this)
        if (Settings.enabled(this)) startForegroundService(Intent(this, ClockService::class.java).setAction(ClockService.RELOAD))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
    }

    private fun renderSlots() {
        slotList.removeAllViews()
        slots.forEachIndexed { index, slot ->
            val row = LinearLayout(this)
            fun timeButton(start: Boolean) = Button(this).apply {
                text = Schedule.clock(if (start) slot.start else slot.end)
                setOnClickListener {
                    val value = if (start) slots[index].start else slots[index].end
                    TimePickerDialog(this@MainActivity, { _, hour, minute ->
                        val current = slots[index]
                        slots[index] = if (start) current.copy(start = hour * 60 + minute) else current.copy(end = hour * 60 + minute)
                        renderSlots()
                    }, value / 60, value % 60, true).show()
                }
            }
            row.addView(timeButton(true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply { text = " 至 " })
            row.addView(timeButton(false), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply { text = "删除"; setOnClickListener { slots.removeAt(index); renderSlots() } })
            slotList.addView(row)
        }
    }

    private fun readSchedule(): Schedule? {
        val selected = days.mapIndexedNotNull { i, check -> if (check.isChecked) i + 1 else null }.toSet()
        if (selected.isEmpty() || slots.isEmpty()) {
            status.text = "请至少选择一天，并添加一个时段"; return null
        }
        return Schedule(selected, slots.toList(), mode.selectedItemPosition, intervals[interval.selectedItemPosition])
    }

    private fun showStatus(message: String, schedule: Schedule) {
        val now = ZonedDateTime.now()
        val next = schedule.next(now, now)
        status.text = "$message\n${Schedule.modes[schedule.mode]}\n下一次计划：${next?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "没有可用时间"}"
    }

    override fun onInit(result: Int) {
        speechInitialized = true
        if (result == TextToSpeech.SUCCESS) {
            val language = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            speechReady = language != null && language >= TextToSpeech.LANG_AVAILABLE
        }
        if (previewPending) {
            if (speechReady) preview() else { previewPending = false; status.text = "中文语音不可用，请在系统文字转语音设置中安装中文语音" }
        }
    }

    private fun preview() {
        previewPending = false
        val now = ZonedDateTime.now()
        val result = tts?.speak("现在时间，${now.hour}点${now.minute}分", TextToSpeech.QUEUE_FLUSH, null, "preview")
        status.text = if (result == TextToSpeech.SUCCESS) "已试听当前时间（不受星期或时段限制）" else "试听失败，请检查系统语音设置"
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
