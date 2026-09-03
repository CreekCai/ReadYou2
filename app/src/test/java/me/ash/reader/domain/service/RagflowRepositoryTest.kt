package me.ash.reader.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagflowRepositoryTest {

    @Test
    fun `recognizes Ragflow streaming completion markers`() {
        assertTrue(isRagflowCompletionEvent("true"))
        assertTrue(isRagflowCompletionEvent("TRUE"))
        assertTrue(isRagflowCompletionEvent("[DONE]"))
    }

    @Test
    fun `does not treat answer payload as completion`() {
        assertFalse(isRagflowCompletionEvent("{\"code\":0,\"data\":{}}"))
        assertFalse(isRagflowCompletionEvent(""))
    }

    @Test
    fun `recognizes first non-empty Ragflow answer`() {
        assertTrue(hasRagflowAnswer("{\"code\":0,\"data\":{\"answer\":\"OK\"}}"))
        assertFalse(hasRagflowAnswer("{\"code\":0,\"data\":{\"answer\":\"\"}}"))
        assertFalse(hasRagflowAnswer("true"))
    }

    @Test
    fun `recognizes missing remote documents`() {
        assertTrue(isMissingRagflowDocument(IllegalStateException("RAGFlow HTTP 404")))
        assertTrue(isMissingRagflowDocument(IllegalStateException("Document not found")))
        assertTrue(isMissingRagflowDocument(IllegalStateException("you don't own the document 42")))
        assertTrue(isMissingRagflowDocument(IllegalStateException("文档不存在")))
        assertFalse(isMissingRagflowDocument(IllegalStateException("RAGFlow HTTP 500")))
    }

    @Test
    fun `remote article identity survives a reinstall`() {
        val first = ragflowDocumentPrefix(
            feedUrl = "https://example.com/feed.xml",
            articleLink = "https://example.com/posts/42",
            title = "Original title",
            publishedAtMillis = 1L,
        )
        val restored = ragflowDocumentPrefix(
            feedUrl = "https://example.com/feed-moved.xml",
            articleLink = "https://example.com/posts/42",
            title = "Updated title",
            publishedAtMillis = 2L,
        )

        assertEquals(first, restored)
        assertTrue(first.startsWith(RAGFLOW_DOCUMENT_PREFIX))
    }

    @Test
    fun `articles without links use stable fallback fields`() {
        val first = ragflowDocumentPrefix("https://example.com/feed.xml", "", "A title", 42L)
        val restored = ragflowDocumentPrefix("https://example.com/feed.xml", "", "A title", 42L)

        assertEquals(first, restored)
    }
}
