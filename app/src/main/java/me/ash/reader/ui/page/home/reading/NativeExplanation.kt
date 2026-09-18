package me.ash.reader.ui.page.home.reading

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.*
import me.ash.reader.domain.service.*
import me.ash.reader.ui.component.reader.bodyStyle

class NativeExplanationState(private val scope: CoroutineScope, val answer: suspend (ExplanationRequest) -> ExplanationAnswer) {
    var paragraphId by mutableStateOf(-1)
    var selected by mutableStateOf("")
    var paragraph by mutableStateOf("")
    var hidden by mutableStateOf(false)
    var editing by mutableStateOf(false)
    var draft by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var history by mutableStateOf<List<ExplanationTurn>>(emptyList())
    var answers by mutableStateOf<List<ExplanationAnswer>>(emptyList())
    private var lastQuestion = ""
    private var job: Job? = null
    private var generation = 0
    fun select(id: Int, text: String, whole: String, ask: Boolean) {
        generation++; job?.cancel(); paragraphId = id; selected = text; paragraph = whole
        history = emptyList(); answers = emptyList(); hidden = false; error = null; loading = false; draft = ""; editing = ask
        if (!ask) send("选中内容在本文里是什么意思？请用两三句话解释。")
    }
    fun send(question: String) {
        if (loading || question.isBlank()) return
        editing = false; loading = true; error = null; lastQuestion = question
        val id = paragraphId
        val token = ++generation
        job = scope.launch {
            try {
                val result = answer(ExplanationRequest(selected, paragraph, question, history))
                ensureActive()
                if (id == paragraphId && token == generation) { answers = answers + result; history = history + ExplanationTurn(question, result.text); draft = "" }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (id == paragraphId && token == generation) error = "解释未完成，请检查网络、大模型配置或配额后重试。" }
            finally { if (id == paragraphId && token == generation) loading = false }
        }
    }
    fun stop() { generation++; job?.cancel(); loading = false }
    fun retry() = send(lastQuestion)
}

@Composable
fun ExplainableParagraph(text: AnnotatedString, style: TextStyle, modifier: Modifier, id: Int, state: NativeExplanationState) {
    val clipboard = LocalClipboard.current
    val view = LocalView.current
    val capture = remember(id) { arrayOfNulls<(String) -> Unit>(1) }
    val wrappedClipboard = remember(clipboard, id) {
        object : Clipboard by clipboard {
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                val action = capture[0]
                if (action != null) { capture[0] = null; action(clipEntry?.clipData?.getItemAt(0)?.text?.toString().orEmpty()) }
                else clipboard.setClipEntry(clipEntry)
            }
        }
    }
    val toolbar = remember(view, id, state) { ReadingSelectionToolbar(view) { copy, ask ->
        capture[0] = { selected -> if (selected.isNotBlank()) state.select(id, selected, text.text, ask) }
        copy()
        capture[0] = null
    } }
    DisposableEffect(toolbar) { onDispose { toolbar.hide() } }
    Column {
        CompositionLocalProvider(LocalClipboard provides wrappedClipboard, LocalTextToolbar provides toolbar) {
            SelectionContainer { Text(text, style = style, modifier = modifier) }
        }
        if (state.paragraphId == id) {
            if (state.hidden) TextButton(onClick = { state.hidden = false }) { Text("查看释义", style = style) }
            else NativeNote(state, modifier)
        }
    }
}

private class ReadingSelectionToolbar(private val view: View, private val explain: (() -> Unit, Boolean) -> Unit) : TextToolbar {
    private var mode: ActionMode? = null
    private var rect = Rect.Zero
    private var copy: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null
    override val status get() = if (mode == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown
    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
        this.rect = rect; copy = onCopyRequested; selectAll = onSelectAllRequested
        if (mode != null) { mode?.invalidate(); mode?.invalidateContentRect(); return }
        mode = view.startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { fill(menu); return true }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); fill(menu); return true }
            private fun fill(menu: Menu) {
                if (copy != null) {
                    menu.add(0, 1, 0, "复制").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(0, 2, 1, "解释").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(0, 3, 2, "提问…").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                if (selectAll != null) menu.add(0, 4, 3, "全选")
            }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    1 -> copy?.invoke()
                    2, 3 -> copy?.let { explain(it, item.itemId == 3) }
                    4 -> { selectAll?.invoke(); return true }
                    else -> return false
                }
                hide(); return true
            }
            override fun onDestroyActionMode(mode: ActionMode) { this@ReadingSelectionToolbar.mode = null }
            override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                outRect.set(this@ReadingSelectionToolbar.rect.left.toInt(), this@ReadingSelectionToolbar.rect.top.toInt(), this@ReadingSelectionToolbar.rect.right.toInt(), this@ReadingSelectionToolbar.rect.bottom.toInt())
            }
        }, ActionMode.TYPE_FLOATING)
    }
    override fun hide() { mode?.finish(); mode = null }
}

@Composable
private fun NativeNote(state: NativeExplanationState, modifier: Modifier) {
    val style = bodyStyle()
    val color = MaterialTheme.colorScheme.primary
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current
    Column(modifier.padding(vertical = 12.dp).drawBehind {
        drawLine(color, Offset(1.5.dp.toPx(), 0f), Offset(1.5.dp.toPx(), size.height), 3.dp.toPx(), StrokeCap.Round)
    }.padding(start = 14.dp)) {
        Text("本文释义 · AI", style = style, color = color)
        state.history.forEachIndexed { index, turn ->
            if (index > 0) Text("问：${turn.question}", style = style, modifier = Modifier.padding(top = 10.dp))
            SelectionContainer { Text(turn.answer, style = style, modifier = Modifier.padding(top = 6.dp)) }
            state.answers.getOrNull(index)?.let { result ->
            if (result.searched) Text("已参考 Google 搜索", style = style, color = color)
            result.sources.forEach { source -> TextButton(onClick = { uriHandler.openUri(source.url) }) { Text(source.title, style = style) } }
            if (result.searchHtml.isNotBlank()) GoogleSearchSuggestions(result.searchHtml)
            }
        }
        if (state.loading) {
            Text("正在结合本文解释…", style = style)
            TextButton(onClick = state::stop) { Text("停止", style = style) }
        }
        state.error?.let { Text(it, style = style); TextButton(onClick = state::retry) { Text("重试", style = style) } }
        if (state.editing) {
            Text("继续追问", style = style, color = color)
            BasicTextField(state.draft, { if (it.length <= 4000) state.draft = it },
                textStyle = style, modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(vertical = 8.dp),
                decorationBox = { inner -> Box { if (state.draft.isEmpty()) Text("输入想问的问题…", style = style); inner() } })
            HorizontalDivider(color = color)
            Row {
                TextButton(onClick = { state.editing = false; keyboard?.hide() }) { Text("取消", style = style) }
                TextButton(onClick = { keyboard?.hide(); state.send(state.draft) }, enabled = state.draft.isNotBlank()) { Text("发送", style = style) }
            }
            LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
        } else if (!state.loading) Row {
            TextButton(onClick = { state.editing = true }) { Text("继续问", style = style) }
            TextButton(onClick = { state.stop(); state.hidden = true }) { Text("收起", style = style) }
        }
    }
}

@Composable
private fun GoogleSearchSuggestions(html: String) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        factory = { android.webkit.WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = false
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    request?.url?.toString()?.takeIf { it.startsWith("https://") }?.let(uri::openUri)
                    return true
                }
            }
        } },
        onRelease = { it.stopLoading(); it.destroy() },
        update = { if (it.tag != html) { it.tag = html; it.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) } },
    )
}
