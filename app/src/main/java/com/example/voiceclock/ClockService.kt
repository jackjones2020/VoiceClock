package com.example.voiceclock

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ClockService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initialized = false
    private var pendingSpeech: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var schedule = Schedule()
    private var next: ZonedDateTime? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val tracker = SpeechTracker()
    private var activeId: String? = null
    private var problem: String? = null
    private val speechTimeout = Runnable {
        activeId?.let(tracker::fail)
        activeId = null; pendingSpeech = null
        tts?.stop()
        problem = "语音播放超时，请检查中文语音和后台运行设置"
        releaseWakeLock(); updateNotification()
    }

    override fun onCreate() {
        super.onCreate()
        SpeechHistory(this).use { it.clearExpired() }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("voice_clock", "语音报时", NotificationManager.IMPORTANCE_LOW)
        )
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                if (TimeSpeech.isChime(utteranceId)) return
                tracker.finish(utteranceId)?.let { record -> SpeechHistory(this@ClockService).use { it.add(record) } }
                handler.post { finished(utteranceId, null) }
            }
            override fun onError(utteranceId: String) {
                val id = TimeSpeech.speechId(utteranceId)
                tracker.fail(id)
                handler.post {
                    if (activeId == id && TimeSpeech.isChime(utteranceId)) tts?.stop()
                    finished(id, "报时失败，请检查系统语音和提示音播放")
                }
            }
            override fun onStop(utteranceId: String, interrupted: Boolean) {
                val id = TimeSpeech.speechId(utteranceId)
                tracker.fail(id)
                handler.post { finished(id, "报时被中断") }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.enabled(this)) { ClockAlarms.cancel(this); stopSelf(); return START_NOT_STICKY }
        schedule = Settings.load(this)
        next = ClockAlarms.next(this)
        startForeground(1, notification())
        val now = ZonedDateTime.now()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val reset = intent?.action == START || intent?.action == RELOAD
        val savedAnchor = prefs.getLong("anchor", 0)
        val anchor = if (reset || savedAnchor == 0L) now else Instant.ofEpochMilli(savedAnchor).atZone(ZoneId.systemDefault())
        if (reset || savedAnchor == 0L) prefs.edit().putLong("anchor", anchor.toInstant().toEpochMilli()).apply()
        val alarmDue = intent?.getLongExtra("due", 0) ?: 0
        val validAlarm = intent?.action == ALARM && alarmDue != 0L && alarmDue == next?.toInstant()?.toEpochMilli() && !now.isBefore(next)
        val speak = (intent?.action == START || validAlarm) && schedule.allows(now)
        // Ignore old alarm deliveries after stop/reload and consume a due alarm only once.
        if (reset || validAlarm || next == null || !next!!.isAfter(now) || intent?.action == RESCHEDULE) {
            next = schedule.next(now, anchor)
        }
        // Re-register even a future persisted alarm: package updates or OS cleanup may have removed it.
        if (!ClockAlarms.schedule(this, next)) {
            next = null; problem = "请打开应用，允许“闹钟和提醒”权限后恢复报时"
        } else problem = null
        if (speak) {
            holdForSpeech()
            pendingSpeech = if (intent?.action == START) "开启报时" else "自动报时"
            if (ready) announce() else if (initialized) {
                pendingSpeech = null; problem = "中文语音不可用，请检查系统语音设置"; releaseWakeLock()
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onInit(status: Int) {
        initialized = true
        if (status == TextToSpeech.SUCCESS) {
            val language = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            ready = language != null && language >= TextToSpeech.LANG_AVAILABLE &&
                tts?.let { TimeSpeech.configure(it, packageName) } == true
        }
        if (pendingSpeech != null) {
            if (ready) announce() else { pendingSpeech = null; problem = "中文语音不可用，请检查系统语音设置"; releaseWakeLock() }
        }
        updateNotification()
    }

    private fun announce() {
        val source = pendingSpeech ?: return
        pendingSpeech = null
        val now = ZonedDateTime.now()
        if (!schedule.allows(now)) { releaseWakeLock(); return }
        activeId?.let(tracker::fail)
        val text = "现在时间，${now.hour}点${now.minute}分"
        val id = tracker.begin(SpeechRecord(now, text, source))
        activeId = id
        if (TimeSpeech.speak(tts, text, id) != TextToSpeech.SUCCESS) {
            tracker.fail(id); finished(id, "报时失败，请检查系统中文语音设置")
        }
    }
    private fun holdForSpeech() {
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voiceclock:speech").apply { acquire(60_000) }
        handler.postDelayed(speechTimeout, 55_000)
    }
    private fun releaseWakeLock() {
        handler.removeCallbacks(speechTimeout)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }
    private fun finished(id: String, error: String?) {
        if (id != activeId) return
        activeId = null; problem = error; releaseWakeLock(); updateNotification()
    }
    private fun updateNotification() {
        if (Settings.enabled(this)) getSystemService(NotificationManager::class.java).notify(1, notification())
    }
    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val detail = problem ?: if (!ready) "正在准备中文语音；如无声音请检查系统语音设置" else
            "下次：${next?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "没有可用时间"}"
        return Notification.Builder(this, "voice_clock").setContentTitle("语音报时已开启")
            .setContentText(detail).setStyle(Notification.BigTextStyle().bigText("${Schedule.modes[schedule.mode]} · $detail"))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(pending).setOngoing(true).build()
    }
    override fun onDestroy() {
        tracker.clear(); handler.removeCallbacksAndMessages(null); releaseWakeLock()
        tts?.stop(); tts?.shutdown(); tts = null
        // Keep the system alarm when Android reclaims the service; explicit stop cancels it in the UI.
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        const val START = "com.example.voiceclock.START"
        const val RELOAD = "com.example.voiceclock.RELOAD"
        const val RESTORE = "com.example.voiceclock.RESTORE"
        const val RESCHEDULE = "com.example.voiceclock.RESCHEDULE"
        const val ALARM = "com.example.voiceclock.ALARM"
    }
}
