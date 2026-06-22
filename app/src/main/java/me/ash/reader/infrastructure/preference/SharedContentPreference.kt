package me.ash.reader.infrastructure.preference

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.sharedContent
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.orNotEmpty
import me.ash.reader.ui.ext.put
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

val LocalSharedContent = compositionLocalOf<SharedContentPreference> { SharedContentPreference.default }

sealed class SharedContentPreference(val value: Int) : Preference() {
    object TitleAndLink : SharedContentPreference(1)
    object FullContent : SharedContentPreference(2)
    object TypeCho : SharedContentPreference(3)
    object GetNote : SharedContentPreference(4)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.sharedContent,
                value
            )
        }
    }

    @Stable
    fun toDesc(context: Context): String =
        when (this) {
            TitleAndLink -> context.getString(R.string.title_and_link)
            FullContent -> context.getString(R.string.share_original_content)
            TypeCho -> context.getString(R.string.share_to_typecho)
            GetNote -> context.getString(R.string.share_to_get_note)
        }

    fun share(
        context: Context,
        title: String?,
        link: String?,
        content: String? = null,
        typeChoEndpoint: String = "",
        typeChoHomeUrl: String = "",
        typeChoUsername: String = "",
        typeChoPassword: String = "",
        typeChoExpirationMinutes: String = "",
        getNoteApiKey: String = "",
        getNoteClientId: String = "",
        getNoteTopicId: String = "",
    ) {
        when (this) {
            TitleAndLink -> share(context, title.orNotEmpty { it + "\n" } + link.orEmpty())
            FullContent -> share(context, content.orEmpty().ifBlank { link.orEmpty() })
            TypeCho ->
                uploadToTypeCho(
                    context = context,
                    endpoint = typeChoEndpoint,
                    homeUrl = typeChoHomeUrl,
                    username = typeChoUsername,
                    password = typeChoPassword,
                    expirationMinutes = typeChoExpirationMinutes,
                    title = title.orEmpty(),
                    link = link.orEmpty(),
                    content = content.orEmpty(),
                )
            GetNote ->
                uploadToGetNote(
                    context = context,
                    apiKey = getNoteApiKey,
                    clientId = getNoteClientId,
                    topicId = getNoteTopicId,
                    title = title.orEmpty(),
                    link = link.orEmpty(),
                    content = content.orEmpty(),
                )
        }
    }

    private fun share(context: Context, content: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        }, context.getString(R.string.share)))
    }

    private fun uploadToTypeCho(
        context: Context,
        endpoint: String,
        homeUrl: String,
        username: String,
        password: String,
        expirationMinutes: String,
        title: String,
        link: String,
        content: String,
    ) {
        if (
            endpoint.isBlank() ||
                homeUrl.isBlank() ||
                username.isBlank() ||
                password.isBlank()
        ) {
            toast(context, context.getString(R.string.typecho_missing_config))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val preparedContent =
                    buildString {
                        append(content.ifBlank { link })
                        if (link.isNotBlank()) {
                            append("\n\n")
                            append(link)
                        }
                    }
                        .prepareTypeChoContent(
                            endpoint = endpoint.trim(),
                            username = username,
                            password = password,
                            baseUrl = link,
                        )
                val body =
                    xmlRpcBody(
                        username = username,
                        password = password,
                        title = title,
                        content = preparedContent,
                        categories = listOfNotNull(
                            expirationMinutes.toTypeChoExpirationCategory().takeIf { it.isNotBlank() }
                        ),
                    )
                val response = postXmlRpc(endpoint.trim(), body)
                val responseText = response.body
                if (response.code !in 200..299) {
                    error("HTTP ${response.code}${responseText.shortErrorSuffix()}")
                }
                if (responseText.contains("<fault>", ignoreCase = true)) {
                    error(responseText.extractXmlRpcFault().ifBlank { "XML-RPC fault" })
                }
                val postId = responseText.extractXmlRpcValue()
                if (postId.isBlank()) {
                    error("Missing post id")
                }
                val postUrl = typeChoPostUrl(homeUrl, postId)
                copyToClipboard(
                    context = context,
                    label = title.ifBlank { context.getString(R.string.share_to_typecho) },
                    text = title.orNotEmpty { "$it\n" } + postUrl,
                )
            }.onSuccess {
                toast(context, context.getString(R.string.typecho_upload_success_copied))
            }.onFailure {
                toast(context, context.getString(R.string.typecho_upload_failed, it.message ?: "unknown"))
            }
        }
    }

    private data class XmlRpcResponse(val code: Int, val body: String)
    private data class HttpTextResponse(val code: Int, val body: String)

    private fun postXmlRpc(endpoint: String, body: String): XmlRpcResponse {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(30).toInt()
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("User-Agent", "ReadYou TypeCho Publisher")
            setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            setFixedLengthStreamingMode(payload.size)
        }
        return try {
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            XmlRpcResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadToGetNote(
        context: Context,
        apiKey: String,
        clientId: String,
        topicId: String,
        title: String,
        link: String,
        content: String,
    ) {
        if (apiKey.isBlank() || clientId.isBlank()) {
            toast(context, context.getString(R.string.get_note_missing_config))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val body =
                    Gson().toJson(
                        buildMap<String, Any> {
                            put("note_type", "plain_text")
                            put("title", title.ifBlank { context.getString(R.string.share_to_get_note) })
                            val markdownContent = content.toGetNoteMarkdown(link)
                            put(
                                "content",
                                buildString {
                                    append(markdownContent)
                                    if (link.isNotBlank()) {
                                        append("\n\n")
                                        append("[原文链接]($link)")
                                    }
                                }.trim(),
                            )
                            if (topicId.isNotBlank()) put("topic_id", topicId.trim())
                        }
                    )
                val response = postGetNote(apiKey.trim(), clientId.trim(), body)
                if (response.code !in 200..299) {
                    error("HTTP ${response.code}${response.body.shortErrorSuffix()}")
                }
                val root = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull()
                    ?: error("Invalid JSON response")
                val success = root.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean
                if (success == false) {
                    error(root.getApiErrorMessage().ifBlank { "API returned failure" })
                }
                val noteId =
                    root.getAsJsonObjectOrNull("data")
                        ?.get("note_id")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        .orEmpty()
                if (noteId.isBlank()) {
                    error(root.getApiErrorMessage().ifBlank { "Missing note id" })
                }
            }.onSuccess {
                toast(context, context.getString(R.string.get_note_upload_success))
            }.onFailure {
                toast(context, context.getString(R.string.get_note_upload_failed, it.message ?: "unknown"))
            }
        }
    }

    private fun postGetNote(apiKey: String, clientId: String, body: String): HttpTextResponse {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val connection =
            (URL("https://openapi.biji.com/open/api/v1/resource/note/save").openConnection() as HttpURLConnection)
                .apply {
                    connectTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
                    readTimeout = TimeUnit.SECONDS.toMillis(30).toInt()
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("User-Agent", "ReadYou GetNote Publisher")
                    setRequestProperty("Authorization", apiKey)
                    setRequestProperty("X-Client-ID", clientId)
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setFixedLengthStreamingMode(payload.size)
                }
        return try {
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpTextResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun String.toGetNoteMarkdown(baseUrl: String): String {
        val source = trim()
        if (source.isBlank()) return ""
        if (!source.containsHtmlTag()) return source

        val body = Jsoup.parseBodyFragment(source, baseUrl).body()
        return body.childNodes()
            .joinToString("\n\n") { it.toMarkdown().trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.containsHtmlTag(): Boolean =
        Regex("""<\s*/?\s*[a-zA-Z][^>]*>""").containsMatchIn(this)

    private data class TypeChoImage(
        val name: String,
        val contentType: String,
        val bytes: ByteArray,
    )

    private fun String.prepareTypeChoContent(
        endpoint: String,
        username: String,
        password: String,
        baseUrl: String,
    ): String {
        val source = trim()
        if (source.isBlank() || !source.containsHtmlTag()) return source

        val body = Jsoup.parseBodyFragment(source, baseUrl).body()
        body.select("img").forEach { image ->
            val imageUrl = image.absUrl("src").ifBlank { image.attr("src") }
            if (imageUrl.isNotBlank()) {
                uploadTypeChoImage(
                    endpoint = endpoint,
                    username = username,
                    password = password,
                    imageUrl = imageUrl,
                )?.let { uploadedUrl ->
                    image.attr("src", uploadedUrl)
                    image.removeAttr("srcset")
                    image.removeAttr("data-src")
                }
            }
            image.removeAttr("width")
            image.removeAttr("height")
            image.attr("loading", "lazy")
            image.attr("decoding", "async")
            image.normalizeTypeChoImageStyle()
        }
        return body.html()
    }

    private fun uploadTypeChoImage(
        endpoint: String,
        username: String,
        password: String,
        imageUrl: String,
    ): String? =
        runCatching {
            val image = downloadTypeChoImage(imageUrl)
            val body =
                xmlRpcMediaBody(
                    username = username,
                    password = password,
                    name = image.name,
                    contentType = image.contentType,
                    bits = Base64.encodeToString(image.bytes, Base64.NO_WRAP),
                )
            val response = postXmlRpc(endpoint, body)
            val responseText = response.body
            if (response.code !in 200..299) {
                error("HTTP ${response.code}${responseText.shortErrorSuffix()}")
            }
            if (responseText.contains("<fault>", ignoreCase = true)) {
                error(responseText.extractXmlRpcFault().ifBlank { "XML-RPC fault" })
            }
            responseText.extractXmlRpcMemberValue("url").ifBlank {
                responseText.extractXmlRpcValue()
            }.ifBlank {
                error("Missing uploaded image url")
            }
        }.getOrNull()

    private fun downloadTypeChoImage(imageUrl: String): TypeChoImage {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(30).toInt()
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ReadYou TypeCho Publisher")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
            val contentType =
                connection.contentType
                    ?.substringBefore(";")
                    ?.trim()
                    ?.takeIf { it.startsWith("image/", ignoreCase = true) }
                    ?: imageUrl.inferImageContentType()
            TypeChoImage(
                name = imageUrl.inferImageFileName(contentType),
                contentType = contentType,
                bytes = connection.inputStream.use { it.readBytes() },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun Element.normalizeTypeChoImageStyle() {
        val declarations =
            attr("style")
                .split(";")
                .mapNotNull { declaration ->
                    val parts = declaration.split(":", limit = 2)
                    val name = parts.getOrNull(0)?.trim()?.lowercase().orEmpty()
                    val value = parts.getOrNull(1)?.trim().orEmpty()
                    if (name.isBlank() || value.isBlank()) return@mapNotNull null
                    name to value
                }
                .toMap(LinkedHashMap())
        declarations["max-width"] = "100%"
        declarations["height"] = "auto"
        attr("style", declarations.entries.joinToString("; ") { "${it.key}: ${it.value}" })
    }

    private fun Node.toMarkdown(): String =
        when (this) {
            is TextNode -> text()
            is Element -> elementToMarkdown()
            else -> childNodes().joinToString("") { it.toMarkdown() }
        }

    private fun Element.elementToMarkdown(): String {
        val tag = tagName().lowercase()
        return when (tag) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = tag.removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1
                "${"#".repeat(level)} ${childrenMarkdown().trim()}"
            }
            "p" -> childrenMarkdown().trim()
            "br" -> "  \n"
            "strong", "b" -> childrenMarkdown().trim().takeIf { it.isNotBlank() }?.let { "**$it**" }.orEmpty()
            "em", "i" -> childrenMarkdown().trim().takeIf { it.isNotBlank() }?.let { "_${it}_" }.orEmpty()
            "code" -> {
                val code = text()
                if (parent()?.tagName()?.lowercase() == "pre") code else "`$code`"
            }
            "pre" -> "\n```\n${text().trimEnd()}\n```\n"
            "a" -> {
                val label = childrenMarkdown().trim().ifBlank { text().trim() }
                val href = absUrl("href").ifBlank { attr("href") }
                if (href.isBlank()) label else "[$label]($href)"
            }
            "img" -> {
                val alt = attr("alt").trim()
                val src = absUrl("src").ifBlank { attr("src") }
                if (src.isBlank()) "" else "![$alt]($src)"
            }
            "ul" -> listItems(ordered = false)
            "ol" -> listItems(ordered = true)
            "li" -> childrenMarkdown().trim()
            "blockquote" ->
                childrenMarkdown()
                    .trim()
                    .lineSequence()
                    .joinToString("\n") { "> $it" }
            "hr" -> "---"
            "table" -> tableMarkdown()
            "thead", "tbody", "tfoot", "tr", "th", "td" -> childrenMarkdown().trim()
            "script", "style", "noscript" -> ""
            else -> childrenMarkdown().trim()
        }
    }

    private fun Element.childrenMarkdown(): String =
        childNodes().joinToString("") { it.toMarkdown() }

    private fun Element.listItems(ordered: Boolean): String =
        children()
            .filter { it.tagName().equals("li", ignoreCase = true) }
            .mapIndexed { index, item ->
                val prefix = if (ordered) "${index + 1}. " else "- "
                item.childrenMarkdown()
                    .trim()
                    .lineSequence()
                    .mapIndexed { lineIndex, line ->
                        if (lineIndex == 0) prefix + line else "  $line"
                    }
                    .joinToString("\n")
            }
            .joinToString("\n")

    private fun Element.tableMarkdown(): String {
        val rows = select("tr").map { row ->
            row.select("th,td").map { it.childrenMarkdown().trim().replace("|", "\\|") }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return ""
        val header = rows.first()
        val separator = header.map { "---" }
        val bodyRows = rows.drop(1)
        return buildString {
            appendLine(header.joinToString(" | ", "| ", " |"))
            appendLine(separator.joinToString(" | ", "| ", " |"))
            bodyRows.forEach { appendLine(it.joinToString(" | ", "| ", " |")) }
        }.trim()
    }

    private fun xmlRpcBody(
        username: String,
        password: String,
        title: String,
        content: String,
        categories: List<String> = emptyList(),
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <methodCall>
          <methodName>metaWeblog.newPost</methodName>
          <params>
            <param><value><string>1</string></value></param>
            <param><value><string>${username.xmlEscape()}</string></value></param>
            <param><value><string>${password.xmlEscape()}</string></value></param>
            <param>
              <value>
                <struct>
                  <member>
                    <name>title</name>
                    <value><string>${title.xmlEscape()}</string></value>
                  </member>
                  <member>
                    <name>description</name>
                    <value><string>${content.xmlEscape()}</string></value>
                  </member>
                  ${categoriesXml(categories)}
                </struct>
              </value>
            </param>
            <param><value><boolean>1</boolean></value></param>
          </params>
        </methodCall>
        """.trim()

    private fun categoriesXml(categories: List<String>): String {
        if (categories.isEmpty()) return ""
        val values = categories.joinToString("\n") {
            "<value><string>${it.xmlEscape()}</string></value>"
        }
        return """
        <member>
          <name>categories</name>
          <value>
            <array>
              <data>
                $values
              </data>
            </array>
          </value>
        </member>
        """.trimIndent()
    }

    private fun xmlRpcMediaBody(
        username: String,
        password: String,
        name: String,
        contentType: String,
        bits: String,
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <methodCall>
          <methodName>metaWeblog.newMediaObject</methodName>
          <params>
            <param><value><string>1</string></value></param>
            <param><value><string>${username.xmlEscape()}</string></value></param>
            <param><value><string>${password.xmlEscape()}</string></value></param>
            <param>
              <value>
                <struct>
                  <member>
                    <name>name</name>
                    <value><string>${name.xmlEscape()}</string></value>
                  </member>
                  <member>
                    <name>type</name>
                    <value><string>${contentType.xmlEscape()}</string></value>
                  </member>
                  <member>
                    <name>bits</name>
                    <value><base64>$bits</base64></value>
                  </member>
                  <member>
                    <name>overwrite</name>
                    <value><boolean>0</boolean></value>
                  </member>
                </struct>
              </value>
            </param>
          </params>
        </methodCall>
        """.trim()

    private fun String.inferImageContentType(): String =
        when (substringBefore("?").substringAfterLast(".", "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> "image/jpeg"
        }

    private fun String.inferImageFileName(contentType: String): String {
        val path =
            runCatching { URI(this).path }
                .getOrNull()
                ?.substringAfterLast("/")
                .orEmpty()
        val cleanName = path.substringBefore("?").sanitizeFileName()
        if (cleanName.isNotBlank() && cleanName.contains(".")) return cleanName
        val extension =
            when (contentType.lowercase()) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "image/svg+xml" -> "svg"
                else -> "jpg"
            }
        return "${cleanName.ifBlank { "readyou-image-${System.currentTimeMillis()}" }}.$extension"
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("""[^\w.\-]+"""), "-")
            .trim('-', '.', '_')
            .take(96)

    private fun String.toTypeChoExpirationCategory(): String {
        val source = trim()
        if (source.isBlank()) return ""
        val match = Regex("""^(\d+(?:\.\d+)?)\s*([^\d\s]+)?$""").matchEntire(source) ?: return source
        val amount = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return source
        val unit = match.groupValues.getOrNull(2).orEmpty().lowercase()
        val expirationDays = when (unit) {
            "", "d", "day", "days", "\u5929", "\u65e5" -> amount
            else -> return source
        }
        return (expirationDays * 1440).roundToLong().coerceAtLeast(0).toString()
    }

    private fun String.xmlEscape(): String =
        filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun String.shortErrorSuffix(): String {
        val text =
            replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(160)
        return if (text.isBlank()) "" else ": $text"
    }

    private fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.getApiErrorMessage(): String {
        val error = getAsJsonObjectOrNull("error")
        return listOfNotNull(
            get("message")?.takeIf { it.isJsonPrimitive }?.asString,
            error?.get("message")?.takeIf { it.isJsonPrimitive }?.asString,
            error?.get("reason")?.takeIf { it.isJsonPrimitive }?.asString,
            get("code")?.takeIf { it.isJsonPrimitive }?.asString,
        ).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun String.extractXmlRpcValue(): String =
        Regex("<(?:string|int|i4)>(.*?)</(?:string|int|i4)>", RegexOption.DOT_MATCHES_ALL)
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.xmlUnescape()
            ?.trim()
            .orEmpty()

    private fun String.extractXmlRpcMemberValue(memberName: String): String =
        Regex(
            "<name>${Regex.escape(memberName)}</name>\\s*<value>\\s*<(?:string|int|i4)>(.*?)</(?:string|int|i4)>",
            RegexOption.DOT_MATCHES_ALL,
        )
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.xmlUnescape()
            ?.trim()
            .orEmpty()

    private fun String.extractXmlRpcFault(): String =
        Regex("<name>faultString</name>\\s*<value>\\s*<string>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.xmlUnescape()
            ?.trim()
            .orEmpty()

    private fun String.xmlUnescape(): String =
        replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun typeChoPostUrl(homeUrl: String, postId: String): String =
        "${homeUrl.trim().trimEnd('/')}/post/${postId.trim().trimStart('/')}"

    private fun copyToClipboard(context: Context, label: String, text: String) {
        Handler(Looper.getMainLooper()).post {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {

        val default = TitleAndLink
        val values = listOf(TitleAndLink, FullContent, TypeCho)

        fun fromPreferences(preferences: Preferences): SharedContentPreference =
            when (preferences[DataStoreKey.keys[sharedContent]?.key as Preferences.Key<Int>]) {
                0 -> TitleAndLink
                1 -> TitleAndLink
                2 -> FullContent
                3 -> TypeCho
                4 -> default
                else -> default
            }
    }
}
