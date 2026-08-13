package me.ash.reader.domain.model.article

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "saved_knowledge_answer",
    indices = [Index("accountId"), Index("createdAt")],
)
data class SavedKnowledgeAnswer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Int,
    val question: String,
    val answer: String,
    val createdAt: Date = Date(),
)
