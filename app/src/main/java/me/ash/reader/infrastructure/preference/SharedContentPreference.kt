package me.ash.reader.infrastructure.preference

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.sharedContent
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.orNotEmpty
import me.ash.reader.ui.ext.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

val LocalSharedContent = compositionLocalOf<SharedContentPreference> { SharedContentPreference.default }

sealed class SharedContentPreference(val value: Int) : Preference() {
    object TitleAndLink : SharedContentPreference(1)
    object FullContent : SharedContentPreference(2)
    object TypeCho : SharedContentPreference(3)

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
                val body =
                    xmlRpcBody(
                        username = username,
                        password = password,
                        title = title,
                        content = buildString {
                            append(content.ifBlank { link })
                            if (link.isNotBlank()) {
                                append("\n\n")
                                append(link)
                            }
                        },
                    )
                val request =
                    Request.Builder()
                        .url(endpoint.trim())
                        .header("User-Agent", "ReadYou TypeCho Publisher")
                        .post(body.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                        .build()
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
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
                }
            }.onSuccess {
                toast(context, context.getString(R.string.typecho_upload_success_copied))
            }.onFailure {
                toast(context, context.getString(R.string.typecho_upload_failed, it.message ?: "unknown"))
            }
        }
    }

    private fun xmlRpcBody(
        username: String,
        password: String,
        title: String,
        content: String,
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
                </struct>
              </value>
            </param>
            <param><value><boolean>1</boolean></value></param>
          </params>
        </methodCall>
        """.trimIndent()

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

    private fun String.extractXmlRpcValue(): String =
        Regex("<(?:string|int|i4)>(.*?)</(?:string|int|i4)>", RegexOption.DOT_MATCHES_ALL)
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
        "${homeUrl.trim().trimEnd('/')}/archives/${postId.trim().trimStart('/')}/"

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
                else -> default
            }
    }
}
