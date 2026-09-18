package me.ash.reader.ui.component.webview

import org.junit.Assert.*
import org.junit.Test
import org.jsoup.Jsoup

class ReadingHtmlSafetyTest {
    @Test fun `reader strips active content before exposing AI bridge`() {
        val html = safeReadingHtml("""<p onclick="ReadYouExplain.ask('1','{}')">safe <b>term</b><img src="https://example.com/image" onerror="evil()"></p><script>evil()</script><iframe srcdoc="evil"></iframe><a href="java&#x09;script:evil()">link</a>""")
        val doc = Jsoup.parse(html)
        assertTrue(doc.select("script,iframe").isEmpty())
        assertFalse(html.contains("onclick"))
        assertFalse(html.contains("onerror"))
        assertFalse(doc.selectFirst("a")!!.hasAttr("href"))
        assertEquals("term", doc.selectFirst("b")!!.text())
        assertEquals("https://example.com/image", doc.selectFirst("img")!!.attr("src"))
    }
}
