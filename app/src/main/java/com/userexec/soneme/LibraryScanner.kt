package com.userexec.soneme

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract

class LibraryScanner(
    private val context: Context,
    private val db: SonemeDatabase
) {
    fun scanTopLevel(): List<LibraryEntry> {
        val entries = mutableListOf<LibraryEntry>()
        for (source in db.sources()) {
            val tree = Uri.parse(source.uri)
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            entries += scanFolder(source, rootId)
        }
        return sort(entries)
    }

    fun scanFolder(location: FolderLocation): List<LibraryEntry> =
        sort(scanFolder(location.source, location.documentId))

    private fun scanFolder(source: SourceFolder, parentDocumentId: String): List<LibraryEntry> {
        val treeUri = Uri.parse(source.uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<LibraryEntry>()

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val documentId = c.getString(idCol)
                val name = c.getString(nameCol) ?: "Unnamed"
                val mime = c.getString(mimeCol) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    result += LibraryEntry(
                        isFolder = true,
                        title = name,
                        subtitle = "Folder",
                        source = source,
                        documentId = documentId
                    )
                } else if (mime.startsWith("audio/") || name.endsWith(".mp3", ignoreCase = true)) {
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    result += audioEntry(documentUri, name)
                }
            }
        }
        return result
    }

    private fun audioEntry(uri: Uri, fallbackName: String): LibraryEntry {
        var title = fallbackName.substringBeforeLast('.', fallbackName)
        var artist = "Unknown"
        var duration = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: title
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Unknown"
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // A malformed or unsupported audio file remains visible with filename metadata.
        } finally {
            runCatching { retriever.release() }
        }

        db.upsertMetadata(uri.toString(), title, artist, duration)
        val state = db.getAudio(uri.toString())
        val percent = state?.takeIf { it.positionMs > 0 && it.durationMs > 0 }
            ?.let { ((it.positionMs * 100) / it.durationMs).toInt().coerceIn(0, 100) }

        return LibraryEntry(
            isFolder = false,
            title = title,
            subtitle = artist,
            durationMs = duration,
            progressPercent = percent,
            uri = uri.toString()
        )
    }

    private fun sort(items: List<LibraryEntry>): List<LibraryEntry> = items.sortedWith(
        compareBy<LibraryEntry> { !it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    )
}
