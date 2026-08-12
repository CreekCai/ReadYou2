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

    @Query("DELETE FROM ragflow_document WHERE articleId = :articleId")
    suspend fun delete(articleId: String)
}
