package com.userexec.soneme

enum class AppView { BOOKS, RECENTS, QUEUE, PLAYER, SOURCES }
enum class RepeatMode { OFF, ONE, ALL }

data class SourceFolder(
    val id: Long,
    val uri: String,
    val name: String
)

data class FolderLocation(
    val source: SourceFolder,
    val documentId: String,
    val displayName: String
)

data class LibraryEntry(
    val isFolder: Boolean,
    val title: String,
    val subtitle: String,
    val durationMs: Long = 0,
    val progressPercent: Int? = null,
    val uri: String? = null,
    val source: SourceFolder? = null,
    val documentId: String? = null
)

data class AudioRecord(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    val lastPlayed: Long
)
