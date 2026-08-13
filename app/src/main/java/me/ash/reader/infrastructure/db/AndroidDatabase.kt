package me.ash.reader.infrastructure.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.ash.reader.domain.model.account.*
import me.ash.reader.domain.model.account.security.DESUtils
import me.ash.reader.domain.model.article.ArchivedArticle
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleAiContent
import me.ash.reader.domain.model.article.OfflineArticle
import me.ash.reader.domain.model.article.RagflowDocument
import me.ash.reader.domain.model.article.SavedKnowledgeAnswer
import me.ash.reader.domain.model.article.KnowledgeSuggestionCache
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleAiContentDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.repository.OfflineArticleDao
import me.ash.reader.domain.repository.RagflowDocumentDao
import me.ash.reader.domain.repository.SavedKnowledgeAnswerDao
import me.ash.reader.domain.repository.KnowledgeSuggestionCacheDao
import me.ash.reader.infrastructure.preference.*
import me.ash.reader.ui.ext.toInt
import java.util.*

@Database(
    entities = [
        Account::class,
        Feed::class,
        Article::class,
        Group::class,
        ArchivedArticle::class,
        ArticleAiContent::class,
        OfflineArticle::class,
        RagflowDocument::class,
        SavedKnowledgeAnswer::class,
        KnowledgeSuggestionCache::class,
    ],
    version = 12,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 5, to = 7),
        AutoMigration(from = 6, to = 7),
    ]
)
@TypeConverters(
    AndroidDatabase.DateConverters::class,
    AccountTypeConverters::class,
    SyncIntervalConverters::class,
    SyncOnStartConverters::class,
    SyncOnlyOnWiFiConverters::class,
    SyncOnlyWhenChargingConverters::class,
    KeepArchivedConverters::class,
    SyncBlockListConverters::class,
)
abstract class AndroidDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun articleAiContentDao(): ArticleAiContentDao
    abstract fun groupDao(): GroupDao
    abstract fun offlineArticleDao(): OfflineArticleDao
    abstract fun ragflowDocumentDao(): RagflowDocumentDao
    abstract fun savedKnowledgeAnswerDao(): SavedKnowledgeAnswerDao
    abstract fun knowledgeSuggestionCacheDao(): KnowledgeSuggestionCacheDao

    companion object {

        private var instance: AndroidDatabase? = null

        fun getInstance(context: Context): AndroidDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AndroidDatabase::class.java,
                    "Reader"
                ).addMigrations(*allMigrations).build().also {
                    instance = it
                }
            }
        }
    }

    class DateConverters {

        @TypeConverter
        fun toDate(dateLong: Long?): Date? {
            return dateLong?.let { Date(it) }
        }

        @TypeConverter
        fun fromDate(date: Date?): Long? {
            return date?.time
        }
    }
}

val allMigrations = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
)

@Suppress("ClassName")
object MIGRATION_1_2 : Migration(1, 2) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE article ADD COLUMN img TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_2_3 : Migration(2, 3) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE article ADD COLUMN updateAt INTEGER DEFAULT ${System.currentTimeMillis()}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncInterval INTEGER NOT NULL DEFAULT ${SyncIntervalPreference.default.value}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnStart INTEGER NOT NULL DEFAULT ${SyncOnStartPreference.default.value.toInt()}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnlyOnWiFi INTEGER NOT NULL DEFAULT ${SyncOnlyOnWiFiPreference.default.value.toInt()}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnlyWhenCharging INTEGER NOT NULL DEFAULT ${SyncOnlyWhenChargingPreference.default.value.toInt()}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN keepArchived INTEGER NOT NULL DEFAULT ${KeepArchivedPreference.default.value}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncBlockList TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_3_4 : Migration(3, 4) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN securityKey TEXT DEFAULT '${DESUtils.empty}'
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_4_5 : Migration(4, 5) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN lastArticleId TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_7_8 : Migration(7, 8) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS article_ai_content (
                articleId TEXT NOT NULL,
                type TEXT NOT NULL,
                status INTEGER NOT NULL,
                content TEXT DEFAULT NULL,
                model TEXT DEFAULT NULL,
                prompt TEXT DEFAULT NULL,
                errorMessage TEXT DEFAULT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(articleId, type),
                FOREIGN KEY(articleId) REFERENCES article(id)
                ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_article_ai_content_articleId
            ON article_ai_content(articleId)
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_8_9 : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS offline_article (
                articleId TEXT NOT NULL PRIMARY KEY,
                accountId INTEGER NOT NULL,
                contentPath TEXT NOT NULL,
                savedAt INTEGER NOT NULL,
                sizeBytes INTEGER NOT NULL,
                status INTEGER NOT NULL,
                errorMessage TEXT,
                FOREIGN KEY(articleId) REFERENCES article(id) ON UPDATE CASCADE ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_article_accountId ON offline_article(accountId)")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS ragflow_document (
                articleId TEXT NOT NULL PRIMARY KEY,
                documentId TEXT,
                contentHash TEXT NOT NULL,
                status INTEGER NOT NULL,
                errorMessage TEXT,
                syncedAt INTEGER NOT NULL,
                FOREIGN KEY(articleId) REFERENCES article(id) ON UPDATE CASCADE ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_ragflow_document_articleId ON ragflow_document(articleId)")
        database.execSQL("DELETE FROM article_ai_content WHERE type = 'insight'")
    }
}

@Suppress("ClassName")
object MIGRATION_9_10 : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_knowledge_answer (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                accountId INTEGER NOT NULL,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_knowledge_answer_accountId ON saved_knowledge_answer(accountId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_knowledge_answer_createdAt ON saved_knowledge_answer(createdAt)")
    }
}

@Suppress("ClassName")
object MIGRATION_10_11 : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS knowledge_suggestion_cache (
                accountId INTEGER NOT NULL PRIMARY KEY,
                questionsJson TEXT NOT NULL,
                snapshotJson TEXT NOT NULL,
                generatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_11_12 : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS knowledge_suggestion_cache_new (
                accountId INTEGER NOT NULL PRIMARY KEY,
                questionsJson TEXT NOT NULL,
                snapshotJson TEXT NOT NULL,
                generatedAt INTEGER NOT NULL,
                status INTEGER NOT NULL,
                errorMessage TEXT,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO knowledge_suggestion_cache_new (
                accountId, questionsJson, snapshotJson, generatedAt,
                status, errorMessage, updatedAt
            )
            SELECT accountId, questionsJson, snapshotJson, generatedAt,
                CASE WHEN questionsJson = '[]' THEN 0 ELSE 3 END,
                NULL, generatedAt
            FROM knowledge_suggestion_cache
            """.trimIndent()
        )
        database.execSQL("DROP TABLE knowledge_suggestion_cache")
        database.execSQL("ALTER TABLE knowledge_suggestion_cache_new RENAME TO knowledge_suggestion_cache")
    }
}
