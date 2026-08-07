package com.userexec.soneme

import android.net.Uri
import android.provider.DocumentsContract

object SourceUtils {
    enum class Relationship { DISTINCT, SAME, INSIDE_EXISTING }

    fun relationship(newTree: Uri, existingTree: Uri): Relationship {
        if (newTree.authority != existingTree.authority) return Relationship.DISTINCT
        val newId = runCatching { DocumentsContract.getTreeDocumentId(newTree) }.getOrNull()
            ?: return Relationship.DISTINCT
        val existingId = runCatching { DocumentsContract.getTreeDocumentId(existingTree) }.getOrNull()
            ?: return Relationship.DISTINCT
        if (newId == existingId) return Relationship.SAME
        return if (newId.startsWith(existingId.trimEnd('/') + "/")) {
            Relationship.INSIDE_EXISTING
        } else Relationship.DISTINCT
    }
}
