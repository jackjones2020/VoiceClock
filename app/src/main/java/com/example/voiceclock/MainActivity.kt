package com.example.voiceclock

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.net.Uri
import android.speech.tts.UtteranceProgressListener
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
    private val previewTracker = SpeechTracker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpeechHistory(this).use { it.clearExpired() }
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
        label("报时前响三声由弱到强的滴，每声 0.3 秒，然后以半速播报。开启后仅在选定星期和时段内报时。")
        button("试听当前时间") {
            previewPending = true
            if (speechReady) preview()
            else if (speechInitialized) { previewPending = false; status.text = "中文语音不可用，请检查系统文字转语音设置" }
            else status.text = "正在准备中文语音…"
        }
        button("查看今日报时记录") { startActivity(Intent(this, HistoryActivity::class.java)) }
        label("锁屏报时使用系统闹钟唤醒；系统可能显示下一次闹钟标记。语音使用闹钟音量。", 14f)
        button("设置锁屏报时权限") { openAlarmPermission() }
        button("设置后台电池用量") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
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
            Settings.save(this, schedule)
            if (!ClockAlarms.permitted(this)) {
                status.text = "请允许“闹钟和提醒”，返回后再次点击开启，才能在锁屏时准时报时"
                openAlarmPermission(); return@button
            }
            Settings.setEnabled(this, true)
            startForegroundService(Intent(this, ClockService::class.java).setAction(ClockService.START))
            showStatus(if (schedule.allows(ZonedDateTime.now())) "已开启，将立即报时" else "已开启，当前不在报时时段", schedule)
        }
        button("停止报时") {
            Settings.setEnabled(this, false); ClockAlarms.cancel(this); this@MainActivity.stopService(Intent(this@MainActivity, ClockService::class.java)); status.text = "已停止，设置已保留"
        }
        status = TextView(this).apply { textSize = 15f; setPadding(0, dp(16), 0, dp(16)) }
        root.addView(status)
        showStatus(if (Settings.enabled(this)) "报时已开启" else "当前未开启", saved)
        setContentView(scroll)
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                if (TimeSpeech.isChime(utteranceId)) return
                previewTracker.finish(utteranceId)?.let { record ->
                    SpeechHistory(this@MainActivity).use { it.add(record) }
                    runOnUiThread { status.text = "试听播放完成，已保存报时记录" }
                }
            }
            override fun onError(utteranceId: String) {
                previewTracker.fail(TimeSpeech.speechId(utteranceId))
                runOnUiThread { status.text = "试听失败，请检查系统语音和提示音播放" }
            }
            override fun onStop(utteranceId: String, interrupted: Boolean) { previewTracker.fail(TimeSpeech.speechId(utteranceId)) }
        })
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
        val next = if (Settings.enabled(this)) ClockAlarms.next(this) else schedule.next(now, now)
        status.text = "$message\n${Schedule.modes[schedule.mode]}\n下一次计划：${next?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "没有可用时间"}"
    }

    override fun onInit(result: Int) {
        speechInitialized = true
        if (result == TextToSpeech.SUCCESS) {
            val language = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            speechReady = language != null && language >= TextToSpeech.LANG_AVAILABLE &&
                tts?.let { TimeSpeech.configure(it, packageName) } == true
        }
        if (previewPending) {
            if (speechReady) preview() else { previewPending = false; status.text = "中文语音不可用，请在系统文字转语音设置中安装中文语音" }
        }
    }

    private fun preview() {
        previewPending = false
        val now = ZonedDateTime.now()
        previewTracker.clear()
        val text = "现在时间，${now.hour}点${now.minute}分"
        val id = previewTracker.begin(SpeechRecord(now, text, "试听"))
        val result = TimeSpeech.speak(tts, text, id)
        if (result != TextToSpeech.SUCCESS) previewTracker.fail(id)
        status.text = if (result == TextToSpeech.SUCCESS) "正在试听当前时间，播放完成后保存记录" else "试听失败，请检查系统语音设置"
    }

    private fun openAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31 && !ClockAlarms.permitted(this)) {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } else status.text = "已具备锁屏报时所需闹钟权限"
    }

    override fun onResume() {
        super.onResume()
        if (!::status.isInitialized) return
        if (Settings.enabled(this)) {
            if (ClockAlarms.permitted(this)) {
                startForegroundService(Intent(this, ClockService::class.java).setAction(ClockService.RESTORE))
            } else status.text = "报时未能运行：请允许“闹钟和提醒”权限"
        }
    }

    override fun onDestroy() { previewTracker.clear(); tts?.stop(); tts?.shutdown(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
