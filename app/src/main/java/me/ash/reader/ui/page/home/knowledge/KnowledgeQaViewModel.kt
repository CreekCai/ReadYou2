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
import me.ash.reader.domain.service.RagSource
import me.ash.reader.domain.service.RagflowRepository

data class QaMessage(val question: String, val answer: String? = null, val sources: List<RagSource> = emptyList(), val error: String? = null)

@HiltViewModel
class KnowledgeQaViewModel @Inject constructor(
    articleDao: ArticleDao,
    private val ragflow: RagflowRepository,
) : ViewModel() {
    val starredCount = articleDao.observeStarredCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _messages = MutableStateFlow<List<QaMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private var sessionId: String? = null
    private var askJob: Job? = null

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
                val result = ragflow.ask(question, sessionId)
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
}
