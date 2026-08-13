package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.ash.reader.domain.model.article.RagflowDocument

@Dao
interface RagflowDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: RagflowDocument)

    @Query("SELECT * FROM ragflow_document WHERE articleId = :articleId")
    suspend fun get(articleId: String): RagflowDocument?

    @Query("SELECT * FROM ragflow_document")
    suspend fun all(): List<RagflowDocument>

    @Query(
        """
        SELECT r.articleId, r.contentHash
        FROM ragflow_document AS r
        INNER JOIN article AS a ON a.id = r.articleId
        WHERE a.accountId = :accountId
        AND a.isStarred = 1
        AND r.status = 1
        ORDER BY r.articleId
        """
    )
    suspend fun syncedKnowledgeSnapshot(accountId: Int): List<RagflowKnowledgeRevision>

    @Query("DELETE FROM ragflow_document WHERE articleId = :articleId")
    suspend fun delete(articleId: String)
}

data class RagflowKnowledgeRevision(
    val articleId: String,
    val contentHash: String,
)
