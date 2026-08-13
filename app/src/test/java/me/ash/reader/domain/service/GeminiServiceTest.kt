package me.ash.reader.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiServiceTest {

    @Test
    fun `keeps an API base URL ending in v1`() {
        assertEquals(
            "https://api.siliconflow.cn/v1",
            normalizeOpenAiBaseUrl("https://api.siliconflow.cn/v1/"),
        )
    }

    @Test
    fun `adds v1 to a server root URL`() {
        assertEquals(
            "https://api.siliconflow.cn/v1",
            normalizeOpenAiBaseUrl("https://api.siliconflow.cn"),
        )
    }

    @Test
    fun `accepts a full chat completions endpoint`() {
        assertEquals(
            "https://api.siliconflow.cn/v1",
            normalizeOpenAiBaseUrl("https://api.siliconflow.cn/v1/chat/completions"),
        )
    }

    @Test
    fun `accepts a full responses endpoint`() {
        assertEquals(
            "https://api.openai.com/v1",
            normalizeOpenAiBaseUrl("https://api.openai.com/v1/responses"),
        )
    }

    @Test
    fun `preserves a custom compatible API path`() {
        assertEquals(
            "https://example.com/openai/v1",
            normalizeOpenAiBaseUrl("https://example.com/openai/v1"),
        )
    }
}
