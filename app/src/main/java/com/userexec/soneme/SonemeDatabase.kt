package com.userexec.soneme

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SonemeDatabase(context: Context) : SQLiteOpenHelper(context, "soneme.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uri TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE audio_state (
                uri TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                position_ms INTEGER NOT NULL DEFAULT 0,
                last_played INTEGER NOT NULL DEFAULT 0,
                sleep_set_position_ms INTEGER NOT NULL DEFAULT -1
            )"""
        )
        db.execSQL(
            """CREATE TABLE queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uri TEXT NOT NULL UNIQUE
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE audio_state ADD COLUMN sleep_set_position_ms INTEGER NOT NULL DEFAULT -1"
            )
        }
    }

    fun sources(): List<SourceFolder> = readableDatabase.rawQuery(
        "SELECT id, uri, name FROM sources ORDER BY id", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(SourceFolder(c.getLong(0), c.getString(1), c.getString(2)))
        }
    }

    fun addSource(uri: String, name: String): Boolean {
        val values = ContentValues().apply {
            put("uri", uri)
            put("name", name)
        }
        return writableDatabase.insertWithOnConflict(
            "sources", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun removeSource(id: Long) {
        writableDatabase.delete("sources", "id=?", arrayOf(id.toString()))
    }

    fun upsertMetadata(uri: String, title: String, artist: String, durationMs: Long) {
        val existing = getAudio(uri)
        val values = ContentValues().apply {
            put("uri", uri)
            put("title", title)
            put("artist", artist)
            put("duration_ms", durationMs)
            put("position_ms", existing?.positionMs ?: 0)
            put("last_played", existing?.lastPlayed ?: 0)
            put("sleep_set_position_ms", existing?.sleepSetPositionMs ?: -1L)
        }
        writableDatabase.insertWithOnConflict(
            "audio_state", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAudio(uri: String): AudioRecord? = readableDatabase.rawQuery(
        "SELECT uri,title,artist,duration_ms,position_ms,last_played,sleep_set_position_ms FROM audio_state WHERE uri=?",
        arrayOf(uri)
    ).use { c ->
        if (!c.moveToFirst()) null else AudioRecord(
            c.getString(0), c.getString(1), c.getString(2),
            c.getLong(3), c.getLong(4), c.getLong(5), c.getLong(6)
        )
    }

    fun saveProgress(uri: String, positionMs: Long, durationMs: Long, markRecent: Boolean) {
        val record = getAudio(uri)
        val safeDuration = if (durationMs > 0) durationMs else record?.durationMs ?: 0
        val safePosition = positionMs.coerceIn(0, safeDuration.coerceAtLeast(positionMs))
        val values = ContentValues().apply {
            put("uri", uri)
            put("title", record?.title ?: uri.substringAfterLast('/'))
            put("artist", record?.artist ?: "Unknown")
            put("duration_ms", safeDuration)
            put("position_ms", safePosition)
            put("last_played", if (markRecent) System.currentTimeMillis() else record?.lastPlayed ?: 0)
            put("sleep_set_position_ms", record?.sleepSetPositionMs ?: -1L)
        }
        writableDatabase.insertWithOnConflict(
            "audio_state", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun recents(): List<AudioRecord> = readableDatabase.rawQuery(
        "SELECT uri,title,artist,duration_ms,position_ms,last_played,sleep_set_position_ms FROM audio_state " +
            "WHERE last_played > 0 ORDER BY last_played DESC", null
    ).use { c -> audioRecords(c) }

    fun clearRecents() {
        writableDatabase.execSQL("UPDATE audio_state SET last_played=0")
    }

    fun setSleepSetPosition(uri: String, positionMs: Long) {
        val values = ContentValues().apply {
            put("sleep_set_position_ms", positionMs.coerceAtLeast(-1L))
        }
        writableDatabase.update("audio_state", values, "uri=?", arrayOf(uri))
    }

    fun queueUris(): List<String> = readableDatabase.rawQuery(
        "SELECT uri FROM queue ORDER BY id", null
    ).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }

    fun queueRecords(): List<AudioRecord> = queueUris().mapNotNull(::getAudio)

    fun addToQueue(uri: String) {
        val values = ContentValues().apply { put("uri", uri) }
        writableDatabase.insertWithOnConflict("queue", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun replaceQueue(uri: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("queue", null, null)
            addToQueue(uri)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun clearQueue() {
        writableDatabase.delete("queue", null, null)
    }

    private fun audioRecords(c: android.database.Cursor): List<AudioRecord> = buildList {
        while (c.moveToNext()) add(
            AudioRecord(
                c.getString(0), c.getString(1), c.getString(2),
                c.getLong(3), c.getLong(4), c.getLong(5), c.getLong(6)
            )
        )
    }
}
