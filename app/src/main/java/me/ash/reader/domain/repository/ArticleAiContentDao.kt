package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.ArticleAiContent

@Dao
interface ArticleAiContentDao {

    @Query(
        """
        SELECT * FROM article_ai_content
        WHERE articleId = :articleId
        AND type = :type
        """
    )
    fun observe(articleId: String, type: String): Flow<ArticleAiContent?>

    @Query(
        """
        SELECT * FROM article_ai_content
        WHERE articleId = :articleId
        AND type = :type
        """
    )
    suspend fun query(articleId: String, type: String): ArticleAiContent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(content: ArticleAiContent)
}
