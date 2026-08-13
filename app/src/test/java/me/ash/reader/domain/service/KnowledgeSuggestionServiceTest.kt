package me.ash.reader.domain.service

import java.util.Date
import me.ash.reader.domain.model.article.KnowledgeSuggestionCache
import me.ash.reader.domain.repository.RagflowKnowledgeRevision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeSuggestionServiceTest {
    private val now = 1_800_000_000_000L
    private val questions = encodeQuestions(listOf("问题一？", "问题二？", "问题三？"))

    @Test
    fun `refreshes when cache is missing or older than seven days`() {
        val snapshot = revisions(20)
        assertTrue(shouldRefreshKnowledgeSuggestions(null, snapshot, now))
        assertTrue(
            shouldRefreshKnowledgeSuggestions(
                cache(snapshot, generatedAt = now - KNOWLEDGE_SUGGESTION_MAX_AGE_MILLIS),
                snapshot,
                now,
            )
        )
    }

    @Test
    fun `does not refresh more than once per day`() {
        val previous = revisions(20)
        val changed = revisions(20, changed = 20)
        assertFalse(
            shouldRefreshKnowledgeSuggestions(
                cache(previous, generatedAt = now - KNOWLEDGE_SUGGESTION_MIN_REFRESH_MILLIS + 1),
                changed,
                now,
            )
        )
    }

    @Test
    fun `refreshes after ten percent of knowledge changes`() {
        val previous = revisions(20)
        val changed = revisions(20, changed = 2)
        assertTrue(
            shouldRefreshKnowledgeSuggestions(
                cache(previous, generatedAt = now - KNOWLEDGE_SUGGESTION_MIN_REFRESH_MILLIS),
                changed,
                now,
            )
        )
    }

    @Test
    fun `refreshes after ten documents change even below ten percent`() {
        val previous = revisions(200)
        val changed = revisions(200, changed = 10)
        assertTrue(
            shouldRefreshKnowledgeSuggestions(
                cache(previous, generatedAt = now - KNOWLEDGE_SUGGESTION_MIN_REFRESH_MILLIS),
                changed,
                now,
            )
        )
    }

    @Test
    fun `keeps cache for small knowledge changes`() {
        val previous = revisions(20)
        val changed = revisions(20, changed = 1)
        assertFalse(
            shouldRefreshKnowledgeSuggestions(
                cache(previous, generatedAt = now - KNOWLEDGE_SUGGESTION_MIN_REFRESH_MILLIS),
                changed,
                now,
            )
        )
    }

    @Test
    fun `refreshes legacy cache when every question is too long`() {
        val snapshot = revisions(20)
        val longQuestions = encodeQuestions(listOf("这是一条超过新版展示长度限制并且无法在移动设备页面完整展示的探索问题".repeat(3) + "？"))
        val cache = KnowledgeSuggestionCache(
            accountId = 1,
            questionsJson = longQuestions,
            snapshotJson = snapshotJson(snapshot),
            generatedAt = Date(now - 1_000),
        )

        assertTrue(shouldRefreshKnowledgeSuggestions(cache, snapshot, now))
    }

    private fun cache(snapshot: List<RagflowKnowledgeRevision>, generatedAt: Long) =
        KnowledgeSuggestionCache(
            accountId = 1,
            questionsJson = questions,
            snapshotJson = snapshotJson(snapshot),
            generatedAt = Date(generatedAt),
        )

    private fun revisions(count: Int, changed: Int = 0) =
        List(count) { index ->
            RagflowKnowledgeRevision("article-$index", if (index < changed) "new-$index" else "hash-$index")
        }

    private fun snapshotJson(snapshot: List<RagflowKnowledgeRevision>) =
        encodeSnapshot(snapshot)
}
