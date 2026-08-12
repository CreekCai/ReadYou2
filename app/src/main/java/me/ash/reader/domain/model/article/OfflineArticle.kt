package me.ash.reader.domain.model.article

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "offline_article",
    foreignKeys = [ForeignKey(
        entity = Article::class,
        parentColumns = ["id"],
        childColumns = ["articleId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("accountId")],
)
data class OfflineArticle(
    @PrimaryKey val articleId: String,
    val accountId: Int,
    val contentPath: String,
    val savedAt: Date,
    val sizeBytes: Long,
    val status: Int = STATUS_AVAILABLE,
    val errorMessage: String? = null,
) {
    companion object {
        const val STATUS_SAVING = 0
        const val STATUS_AVAILABLE = 1
        const val STATUS_FAILED = 2
    }
}
