package me.ash.reader.domain.model.article

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "ragflow_document",
    indices = [Index("articleId")],
    foreignKeys = [ForeignKey(
        entity = Article::class,
        parentColumns = ["id"],
        childColumns = ["articleId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
    )],
)
data class RagflowDocument(
    @PrimaryKey val articleId: String,
    val documentId: String? = null,
    val contentHash: String = "",
    val status: Int = STATUS_PENDING,
    val errorMessage: String? = null,
    val syncedAt: Date = Date(),
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_SYNCED = 1
        const val STATUS_FAILED = 2
    }
}
