package me.ash.reader.ui.page.home.knowledge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.SavedKnowledgeAnswer
import me.ash.reader.domain.repository.SavedKnowledgeAnswerDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.ui.ext.collectAsStateValue

@HiltViewModel
class SavedKnowledgeViewModel @Inject constructor(
    private val dao: SavedKnowledgeAnswerDao,
    accountService: AccountService,
) : ViewModel() {
    val answers = dao.observeAll(accountService.getCurrentAccountId())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedKnowledgePage(
    onBack: () -> Unit,
    viewModel: SavedKnowledgeViewModel = hiltViewModel(),
) {
    val answers = viewModel.answers.collectAsStateValue()
    val pageBackground =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.background
        }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已保存的知识库回答") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = pageBackground,
                        scrolledContainerColor = pageBackground,
                    ),
            )
        },
        containerColor = pageBackground,
    ) { padding ->
        if (answers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.BookmarkBorder, contentDescription = null)
                Spacer(Modifier.height(16.dp))
                Text("还没有保存的回答", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "在星标知识库的回答下方点击“保存回答”，之后可以在这里查看。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                items(answers, key = SavedKnowledgeAnswer::id) { item ->
                    SavedAnswerItem(item, onDelete = { viewModel.delete(item.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SavedAnswerItem(item: SavedKnowledgeAnswer, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                item.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, "删除保存的回答")
            }
        }
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(item.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (expanded) {
            MarkdownAnswer(item.answer, Modifier.fillMaxWidth())
        } else {
            Text(
                item.answer.replace(Regex("[#*_`>|]"), "").replace('\n', ' '),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
