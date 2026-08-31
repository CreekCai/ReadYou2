package me.ash.reader.domain.service

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
}
