package me.ash.reader.domain.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ReadingExplanationTest {
    @Test fun `context contains whole article selected occurrence original and history`() {
        val request = ExplanationRequest("RAG", "第二次出现 RAG 的段落", "区别是什么", listOf(ExplanationTurn("是什么", "检索增强")), "<p>original term</p>")
        val json = JsonParser.parseString(explanationContext("标题", "https://example.com/a", "<p>RAG 开头</p><p>结尾依据</p>", request)).asJsonObject
        assertTrue(json["articleBody"].asString.contains("结尾依据"))
        assertEquals(request.paragraph, json["selectedParagraph"].asString)
        assertEquals("original term", json["originalBeforeTranslation"].asString)
        assertEquals(1, json.getAsJsonArray("conversation").size())
    }
    @Test(expected = IllegalArgumentException::class) fun `oversized context fails instead of silently discarding selection`() {
        explanationContext("", "", "字".repeat(240001), ExplanationRequest("字", "字", "解释"))
    }
    @Test fun `grounding uses citation metadata and hides thought parts`() {
        val result = parseGroundedExplanation("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"private reasoning"},{"text":"这是中文答案。"}]},"groundingMetadata":{"webSearchQueries":["test"],"groundingChunks":[{"web":{"title":"来源","uri":"https://example.com"}}],"groundingSupports":[{"segment":{"text":"这是中文答案。","endIndex":21},"groundingChunkIndices":[0]}],"searchEntryPoint":{"renderedContent":"<div>Search</div>"}}}]}""")
        assertEquals("这是中文答案。[1]", result.text)
        assertTrue(result.searched)
        assertEquals("https://example.com", result.sources.single().url)
        assertEquals("<div>Search</div>", result.searchHtml)
    }
    @Test fun `non grounded answer does not claim web search`() {
        val answer = parseGroundedExplanation("""{"candidates":[{"content":{"parts":[{"text":"释义"}]}}]}""")
        assertFalse(answer.searched)
        assertTrue(answer.sources.isEmpty())
    }
}
