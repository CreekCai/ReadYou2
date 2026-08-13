package me.ash.reader.ui.page.home.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RagSource
import me.ash.reader.domain.service.RagflowRepository

data class QaMessage(val question: String, val answer: String? = null, val sources: List<RagSource> = emptyList(), val error: String? = null)

@HiltViewModel
class KnowledgeQaViewModel @Inject constructor(
    private val articleDao: ArticleDao,
    accountService: AccountService,
    private val ragflow: RagflowRepository,
) : ViewModel() {
    val starredCount = articleDao.observeStarredCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _messages = MutableStateFlow<List<QaMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()
    private val accountId = accountService.getCurrentAccountId()
    private var sessionId: String? = null
    private var askJob: Job? = null

    init {
        viewModelScope.launch {
            val titles = articleDao.queryAllStarred(accountId)
                .map { it.article.title.trim() }
                .filter(String::isNotBlank)
                .distinct()
            if (titles.isEmpty()) {
                _suggestions.value = emptyList()
                return@launch
            }
            _suggestions.value = INSIGHTFUL_QUESTION_POOL.shuffled().take(3)
            val generated = if (ragflow.isConfigured()) {
                ragflow.suggestQuestions(titles.shuffled().take(80)).getOrDefault(emptyList())
            } else {
                emptyList()
            }
            if (generated.isNotEmpty()) {
                _suggestions.value = (generated.shuffled() + INSIGHTFUL_QUESTION_POOL.shuffled())
                    .distinctBy(::normalizedQuestion)
                    .take(3)
            }
        }
    }

    fun ask(question: String) {
        if (question.isBlank() || _loading.value) return
        _messages.value += QaMessage(question)
        request(_messages.value.lastIndex, question.trim())
    }

    fun retry(index: Int) {
        if (_loading.value) return
        val question = _messages.value.getOrNull(index)?.question ?: return
        _messages.value = _messages.value.mapIndexed { itemIndex, item ->
            if (itemIndex == index) QaMessage(question) else item
        }
        request(index, question)
    }

    fun cancel() {
        askJob?.cancel()
        askJob = null
        _loading.value = false
    }

    private fun request(index: Int, question: String) {
        _loading.value = true
        askJob = viewModelScope.launch {
            try {
                val result = ragflow.ask(question, sessionId) { partial ->
                    updateMessage(index, QaMessage(question, partial.answer, partial.sources))
                }
                sessionId = result.sessionId ?: sessionId
                updateMessage(index, QaMessage(question, result.answer, result.sources))
            } catch (_: CancellationException) {
                updateMessage(index, QaMessage(question, error = "已停止生成"))
            } catch (error: Throwable) {
                updateMessage(index, QaMessage(question, error = error.message ?: "提问失败"))
            } finally {
                _loading.value = false
                askJob = null
            }
        }
    }

    private fun updateMessage(index: Int, message: QaMessage) {
        _messages.value = _messages.value.mapIndexed { itemIndex, item ->
            if (itemIndex == index) message else item
        }
    }

    private fun normalizedQuestion(question: String): String = question
        .lowercase()
        .filterNot(Char::isWhitespace)

    companion object {
        private val INSIGHTFUL_QUESTION_POOL = listOf(
            "从我的全部星标文章看，我长期关注的核心主题是什么，它们之间有什么联系？",
            "我的收藏反映出哪些尚未解决的问题或知识盲区？",
            "哪些观点在我的知识库中相互矛盾，分歧背后的关键假设是什么？",
            "如果把这些知识转化为行动，最值得我优先尝试的三件事是什么？",
            "哪些反复出现的信号可能代表我下一步最值得深入的方向？",
            "我的关注点发生了怎样的变化，这可能说明我的需求出现了什么转变？",
            "哪些文章可以组合成一套更完整的认知框架？",
            "知识库里有哪些容易被忽略、但可能影响判断的重要联系？",
            "基于我的收藏，我可能正在做什么决策，还缺少哪些关键信息？",
            "哪些结论得到了多篇文章的共同支持，证据是否足够可靠？",
            "我的知识库中有哪些共识值得保留，又有哪些观点需要重新验证？",
            "如果只能保留五条最有价值的洞察，应该是哪五条，为什么？",
            "有哪些看似无关的主题，其实可以组合成新的解决思路？",
            "从这些文章推断，我最可能关心但还没有主动提出的问题是什么？",
            "哪些知识已经可以形成实践方法，哪些仍停留在观点层面？",
        )
    }
}
