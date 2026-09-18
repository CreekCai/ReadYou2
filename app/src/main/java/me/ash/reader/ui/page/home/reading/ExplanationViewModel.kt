package me.ash.reader.ui.page.home.reading

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import me.ash.reader.domain.service.*

@HiltViewModel
class ExplanationViewModel @Inject constructor(private val service: GeminiService) : ViewModel() {
    suspend fun answer(title: String, url: String, content: String, request: ExplanationRequest) =
        service.explain(title, url, content, request)
}
