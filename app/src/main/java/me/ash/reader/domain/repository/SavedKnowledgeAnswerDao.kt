package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.SavedKnowledgeAnswer

@Dao
interface SavedKnowledgeAnswerDao {
    @Insert
    suspend fun insert(value: SavedKnowledgeAnswer): Long

    @Query("SELECT * FROM saved_knowledge_answer WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun observeAll(accountId: Int): Flow<List<SavedKnowledgeAnswer>>

    @Query("DELETE FROM saved_knowledge_answer WHERE id = :id")
    suspend fun delete(id: Long)
}
