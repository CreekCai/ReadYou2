package me.ash.reader.domain.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.article.OfflineArticle
import me.ash.reader.domain.repository.OfflineArticleDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class OfflineArticleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OfflineArticleDao,
    private val readerCache: ReaderCacheHelper,
    private val client: OkHttpClient,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val root get() = context.filesDir.resolve("offline_articles")

    suspend fun save(value: ArticleWithFeed) = withContext(ioDispatcher) {
        val article = value.article
        val folder = root.resolve(article.accountId.toString()).resolve(hash(article.id))
        folder.mkdirs()
        val htmlFile = folder.resolve("content.html")
        dao.upsert(OfflineArticle(article.id, article.accountId, htmlFile.absolutePath, Date(), 0, OfflineArticle.STATUS_SAVING))
        runCatching {
            val source = readerCache.readOrFetchFullContent(article).getOrElse { article.rawDescription }
            require(source.isNotBlank()) { "文章没有可保存的正文" }
            val document = Jsoup.parseBodyFragment(source, article.link)
            var failed = 0
            document.select("img").forEach { image ->
                val remote = image.absUrl("src").ifBlank { image.absUrl("data-src") }
                if (remote.startsWith("http")) {
                    runCatching {
                        val request = Request.Builder().url(remote).header("User-Agent", "ReadYou Offline Reader").build()
                        client.newCall(request).execute().use { response ->
                            check(response.isSuccessful) { "HTTP ${response.code}" }
                            val extension = response.body.contentType()?.subtype?.substringBefore('+')?.take(5) ?: "img"
                            val target = folder.resolve("${hash(remote)}.$extension")
                            target.outputStream().use { response.body.byteStream().copyTo(it) }
                            image.attr("src", target.toURI().toString())
                            image.removeAttr("srcset").removeAttr("data-src")
                        }
                    }.onFailure { failed++ }
                }
            }
            htmlFile.writeText(document.body().html())
            val size = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            dao.upsert(OfflineArticle(article.id, article.accountId, htmlFile.absolutePath, Date(), size, OfflineArticle.STATUS_AVAILABLE, if (failed > 0) "$failed 张图片未能下载" else null))
        }.onFailure {
            dao.upsert(OfflineArticle(article.id, article.accountId, htmlFile.absolutePath, Date(), 0, OfflineArticle.STATUS_FAILED, it.message))
            throw it
        }
    }

    suspend fun read(articleId: String): String? = withContext(ioDispatcher) {
        dao.get(articleId)?.takeIf { it.status == OfflineArticle.STATUS_AVAILABLE }?.contentPath?.let(::File)?.takeIf(File::exists)?.readText()
    }

    suspend fun remove(articleId: String) = withContext(ioDispatcher) {
        dao.get(articleId)?.contentPath?.let(::File)?.parentFile?.deleteRecursively()
        dao.delete(articleId)
    }

    suspend fun clear(accountId: Int) = dao.all(accountId).forEach { remove(it.articleId) }
    suspend fun clearAll() = dao.all().forEach { remove(it.articleId) }

    suspend fun cleanup(accountId: Int, retentionDays: Int) {
        if (retentionDays <= 0) return
        val before = Date(System.currentTimeMillis() - retentionDays * 86_400_000L)
        dao.olderThan(accountId, before).forEach { remove(it.articleId) }
    }

    suspend fun cleanupAll(retentionDays: Int) {
        if (retentionDays <= 0) return
        val before = Date(System.currentTimeMillis() - retentionDays * 86_400_000L)
        dao.olderThan(before).forEach { remove(it.articleId) }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
