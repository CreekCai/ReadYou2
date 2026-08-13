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
            _suggestions.value = buildSuggestions(
                articleDao.queryLatestRagflowStarred(accountId, limit = 10)
                    .map { it.article.title }
                    .filter(String::isNotBlank)
            )
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

    private fun buildSuggestions(titles: List<String>): List<String> {
        if (titles.isEmpty()) return emptyList()
        val shuffled = titles.distinct().shuffled()
        val primary = shuffled[0]
        val secondary = shuffled.getOrElse(1) { primary }
        val tertiary = shuffled.getOrElse(2) { secondary }
        val candidates = buildList {
            add("《$primary》的核心观点和关键依据是什么？")
            if (primary != secondary) {
                add("对比《$primary》和《$secondary》，它们的观点有哪些联系或分歧？")
            }
            add("结合最近的文章，《$tertiary》带来了哪些值得行动的启发？")
            add("围绕《$secondary》，知识库中还有哪些文章可以相互印证？")
            add("从最近上传的文章看，哪些主题正在反复出现？")
            add("把《$primary》放进最近十篇文章的上下文中，它最重要的价值是什么？")
        }
        return candidates.distinct().shuffled().take(3)
    }
}
