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
import me.ash.reader.domain.repository.SavedKnowledgeAnswerDao
import me.ash.reader.domain.model.article.SavedKnowledgeAnswer
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RagSource
import me.ash.reader.domain.service.RagflowRepository
import me.ash.reader.domain.service.KnowledgeSuggestionService
import me.ash.reader.domain.service.KnowledgeSuggestionState

data class QaMessage(
    val question: String,
    val answer: String? = null,
    val sources: List<RagSource> = emptyList(),
    val error: String? = null,
    val isSaved: Boolean = false,
    val isComplete: Boolean = false,
)

@HiltViewModel
class KnowledgeQaViewModel @Inject constructor(
    private val articleDao: ArticleDao,
    private val savedAnswerDao: SavedKnowledgeAnswerDao,
    accountService: AccountService,
    private val ragflow: RagflowRepository,
    private val suggestionService: KnowledgeSuggestionService,
) : ViewModel() {
    val starredCount = articleDao.observeStarredCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _messages = MutableStateFlow<List<QaMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()
    private val _suggestionState = MutableStateFlow(KnowledgeSuggestionState())
    val suggestionState = _suggestionState.asStateFlow()
    private val accountId = accountService.getCurrentAccountId()
    private var sessionId: String? = null
    private var askJob: Job? = null

    init {
        viewModelScope.launch {
            suggestionService.observeCachedQuestions(accountId).collect { cached ->
                _suggestions.value = cached.shuffled()
            }
        }
        viewModelScope.launch {
            suggestionService.observeState(accountId).collect { state ->
                _suggestionState.value = state
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

    fun save(index: Int) {
        val message = _messages.value.getOrNull(index) ?: return
        if (message.isSaved || !message.isComplete) return
        val answer = message.answer?.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            savedAnswerDao.insert(
                SavedKnowledgeAnswer(
                    accountId = accountId,
                    question = message.question,
                    answer = answer,
                )
            )
            _messages.value = _messages.value.mapIndexed { itemIndex, item ->
                if (itemIndex == index) item.copy(isSaved = true) else item
            }
        }
    }

    private fun request(index: Int, question: String) {
        _loading.value = true
        askJob = viewModelScope.launch {
            try {
                val result = ragflow.ask(question, sessionId) { partial ->
                    updateMessage(index, QaMessage(question, partial.answer, partial.sources))
                }
                sessionId = result.sessionId ?: sessionId
                updateMessage(index, QaMessage(question, result.answer, result.sources, isComplete = true))
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

}
