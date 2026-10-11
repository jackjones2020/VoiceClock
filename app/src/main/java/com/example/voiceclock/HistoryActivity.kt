package com.example.voiceclock

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

class HistoryActivity : Activity() {
    private lateinit var date: TextView
    private lateinit var entries: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val rollover = object : Runnable {
        override fun run() { refresh(); scheduleRollover() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        root.addView(Button(this).apply { text = "返回报时设置"; setOnClickListener { finish() } })
        root.addView(TextView(this).apply { text = "今日报时记录"; textSize = 24f })
        root.addView(TextView(this).apply { text = "只保留当天记录。语音完整播放后记录，包含自动报时和试听。" })
        date = TextView(this).apply { textSize = 18f; setPadding(0, dp(12), 0, dp(8)) }
        root.addView(date)
        root.addView(Button(this).apply { text = "刷新记录"; setOnClickListener { refresh(); scheduleRollover() } })
        entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(entries)
        setContentView(ScrollView(this).apply {
            addView(root)
            setOnApplyWindowInsetsListener { view, insets -> view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom); insets }
        })
    }
    override fun onResume() { super.onResume(); refresh(); scheduleRollover() }
    override fun onPause() { handler.removeCallbacks(rollover); super.onPause() }
    private fun scheduleRollover() {
        handler.removeCallbacks(rollover)
        val now = ZonedDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        handler.postDelayed(rollover, Duration.between(now, midnight).toMillis().coerceAtLeast(1) + 500)
    }
    private fun refresh() {
        date.text = LocalDate.now().toString()
        val rows = SpeechHistory(this).use { it.today() }
        entries.removeAllViews()
        entries.addView(TextView(this).apply { text = if (rows.isEmpty()) "今天暂无成功报时记录" else "今天成功报时 ${rows.size} 次"; textSize = 18f })
        rows.forEach { value -> entries.addView(TextView(this).apply { text = value; textSize = 16f; setPadding(0, dp(8), 0, dp(8)) }) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
