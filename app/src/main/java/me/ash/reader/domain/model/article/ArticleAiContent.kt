package me.ash.reader.domain.model.article

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import java.util.Date

@Entity(
    tableName = "article_ai_content",
    primaryKeys = ["articleId", "type"],
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class ArticleAiContent(
    @ColumnInfo(index = true)
    val articleId: String,
    val type: String,
    val status: Int = Status.IDLE,
    val content: String? = null,
    val model: String? = null,
    val prompt: String? = null,
    val errorMessage: String? = null,
    val updatedAt: Date = Date(),
) {
    object Type {
        const val SUMMARY = "summary"
        const val INSIGHT = "insight"
    }

    object Status {
        const val IDLE = 0
        const val RUNNING = 1
        const val SUCCESS = 2
        const val FAILED = 3
    }
}
