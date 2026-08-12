package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.article.OfflineArticle
import java.util.Date

@Dao
interface OfflineArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: OfflineArticle)

    @Query("SELECT * FROM offline_article WHERE articleId = :articleId")
    suspend fun get(articleId: String): OfflineArticle?

    @Query("SELECT articleId FROM offline_article WHERE status = 1")
    fun observeIds(): Flow<List<String>>

    @Transaction
    @Query("SELECT article.* FROM article INNER JOIN offline_article ON article.id = offline_article.articleId WHERE article.accountId = :accountId AND offline_article.status = 1 ORDER BY offline_article.savedAt DESC")
    fun observeArticles(accountId: Int): Flow<List<ArticleWithFeed>>

    @Query("SELECT * FROM offline_article WHERE accountId = :accountId ORDER BY savedAt DESC")
    suspend fun all(accountId: Int): List<OfflineArticle>

    @Query("SELECT * FROM offline_article")
    suspend fun all(): List<OfflineArticle>

    @Query("SELECT * FROM offline_article WHERE accountId = :accountId AND savedAt < :before")
    suspend fun olderThan(accountId: Int, before: Date): List<OfflineArticle>

    @Query("SELECT * FROM offline_article WHERE savedAt < :before")
    suspend fun olderThan(before: Date): List<OfflineArticle>

    @Query("DELETE FROM offline_article WHERE articleId = :articleId")
    suspend fun delete(articleId: String)
}
