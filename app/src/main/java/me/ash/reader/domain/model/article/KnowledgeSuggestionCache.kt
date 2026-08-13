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
)
