package me.ash.reader.ui.page.home.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    fun ask(question: String) {
        if (question.isBlank() || _loading.value) return
        _messages.value += QaMessage(question)
        _loading.value = true
        viewModelScope.launch {
            runCatching { ragflow.ask(question.trim(), sessionId) }
                .onSuccess { result ->
                    sessionId = result.sessionId ?: sessionId
                    _messages.value = _messages.value.dropLast(1) + QaMessage(question, result.answer, result.sources)
                }
                .onFailure { error ->
                    _messages.value = _messages.value.dropLast(1) + QaMessage(question, error = error.message ?: "提问失败")
                }
            _loading.value = false
        }
    }
}
