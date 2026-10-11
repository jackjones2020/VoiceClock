package com.example.voiceclock

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

class SpeechHistory(context: Context) : SQLiteOpenHelper(context, "speech-history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY, day TEXT NOT NULL, spoken_at TEXT NOT NULL, text TEXT NOT NULL, source TEXT NOT NULL)")
        db.execSQL("CREATE INDEX history_day ON history(day, id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun clearExpired() {
        writableDatabase.delete("history", "day <> ?", arrayOf(LocalDate.now().toString()))
    }
    fun add(record: SpeechRecord) {
        clearExpired()
        // A request started before midnight must not recreate yesterday's history when it finishes.
        if (record.time.toLocalDate() != LocalDate.now()) return
        writableDatabase.insertOrThrow("history", null, ContentValues().apply {
            put("day", record.time.toLocalDate().toString())
            put("spoken_at", record.time.toLocalTime().withNano(0).toString())
            put("text", record.text); put("source", record.source)
        })
    }
    fun today(): List<String> {
        clearExpired()
        val day = LocalDate.now()
        return readableDatabase.query(
            "history", arrayOf("spoken_at", "text", "source"), "day = ?", arrayOf(day.toString()), null, null, "id DESC"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add("${cursor.getString(0)} · ${cursor.getString(2)}\n${cursor.getString(1)}") }
        }
    }
}
