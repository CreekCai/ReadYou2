package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.KnowledgeSuggestionCache

@Dao
interface KnowledgeSuggestionCacheDao {
    @Query("SELECT * FROM knowledge_suggestion_cache WHERE accountId = :accountId")
    fun observe(accountId: Int): Flow<KnowledgeSuggestionCache?>

    @Query("SELECT * FROM knowledge_suggestion_cache WHERE accountId = :accountId")
    suspend fun get(accountId: Int): KnowledgeSuggestionCache?

    @Upsert
    suspend fun upsert(value: KnowledgeSuggestionCache)
}
