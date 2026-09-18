package me.ash.reader.ui.component.webview

import android.webkit.JavascriptInterface
import com.google.gson.Gson
import kotlinx.coroutines.*
import me.ash.reader.domain.service.ExplanationAnswer
import me.ash.reader.domain.service.ExplanationRequest
import org.jsoup.Jsoup

class ExplanationBridge(
    private val view: SelectionWebView,
    private val scope: CoroutineScope,
    private val answer: suspend (ExplanationRequest) -> ExplanationAnswer,
) {
    private val gson = Gson()
    private var job: Job? = null
    private var document = 0
    private var disposed = false

    fun newDocument() { document++; job?.cancel() }
    fun dispose() { disposed = true; newDocument() }

    @JavascriptInterface fun cancel() { view.post { job?.cancel() } }
    @JavascriptInterface fun ask(id: String, payload: String) {
        if (payload.length > 200000 || id.length > 20) return
        view.post {
            if (disposed) return@post
            job?.cancel()
            val generation = document
            job = scope.launch {
                val result = try {
                    val request = gson.fromJson(payload, ExplanationRequest::class.java)
                    gson.toJson(answer(request))
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    gson.toJson(mapOf("error" to "解释未完成，请检查网络、大模型配置或配额后重试。"))
                }
                ensureActive()
                if (!disposed && document == generation) {
                    view.evaluateJavascript("window.ReadYouNotes && window.ReadYouNotes.receive(${gson.toJson(id)}, $result)", null)
                }
            }
        }
    }
}

/** Article HTML is untrusted; it must never execute code with access to the native AI bridge. */
internal fun safeReadingHtml(content: String): String {
    val doc = Jsoup.parseBodyFragment(content)
    doc.select("script,iframe,frame,frameset,object,embed,form,input,textarea,button,link,meta,base,style,svg,math").remove()
    doc.allElements.forEach { element ->
        element.attributes().asList().forEach { attr ->
            val key = attr.key.lowercase()
            val value = attr.value.trim().lowercase().replace(Regex("[\\u0000-\\u0020]"), "")
            if (key.startsWith("on") || key == "srcdoc" || key == "contenteditable" ||
                ((key == "href" || key == "src" || key == "xlink:href") &&
                    (value.startsWith("javascript:") || value.startsWith("vbscript:") || value.startsWith("data:text/html")))) {
                element.removeAttr(attr.key)
            }
        }
    }
    return doc.body().html()
}
