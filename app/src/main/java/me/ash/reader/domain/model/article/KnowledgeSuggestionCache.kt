package me.ash.reader.domain.model.article

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "knowledge_suggestion_cache")
data class KnowledgeSuggestionCache(
    @PrimaryKey val accountId: Int,
    val questionsJson: String,
    val snapshotJson: String,
    val generatedAt: Date = Date(),
    val status: Int = STATUS_READY,
    val errorMessage: String? = null,
    val updatedAt: Date = Date(),
) {
    companion object {
        const val STATUS_PREPARING = 0
        const val STATUS_SYNCING = 1
        const val STATUS_GENERATING = 2
        const val STATUS_READY = 3
        const val STATUS_FAILED = 4
        const val STATUS_CONFIGURATION_REQUIRED = 5
        const val STATUS_INSUFFICIENT_CONTENT = 6
    }
}
