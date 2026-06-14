package me.ash.reader.ui.page.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.GeminiApiKeyPreference
import me.ash.reader.infrastructure.preference.GeminiModelPreference
import me.ash.reader.infrastructure.preference.GeminiInsightModelPreference
import me.ash.reader.infrastructure.preference.GeminiPromptPreference
import me.ash.reader.infrastructure.preference.GeminiTranslationModelPreference
import me.ash.reader.infrastructure.preference.GeminiTranslationPromptPreference
import me.ash.reader.infrastructure.preference.GeminiInsightPromptPreference
import me.ash.reader.infrastructure.preference.LocalGeminiApiKey
import me.ash.reader.infrastructure.preference.LocalGeminiModel
import me.ash.reader.infrastructure.preference.LocalGeminiInsightModel
import me.ash.reader.infrastructure.preference.LocalGeminiPrompt
import me.ash.reader.infrastructure.preference.LocalGeminiTranslationModel
import me.ash.reader.infrastructure.preference.LocalGeminiTranslationPrompt
import me.ash.reader.infrastructure.preference.LocalGeminiInsightPrompt
import me.ash.reader.ui.component.base.FeedbackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiSettingsPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val apiKey = LocalGeminiApiKey.current
    val model = LocalGeminiModel.current
    val translationModel = LocalGeminiTranslationModel.current
    val insightModel = LocalGeminiInsightModel.current
    val prompt = LocalGeminiPrompt.current
    val translationPrompt = LocalGeminiTranslationPrompt.current
    val insightPrompt = LocalGeminiInsightPrompt.current

    var expandedModel by remember { mutableStateOf(false) }
    var expandedTranslationModel by remember { mutableStateOf(false) }
    var expandedInsightModel by remember { mutableStateOf(false) }
    
    var apiKeyText by remember { mutableStateOf(apiKey) }
    var promptState by remember { mutableStateOf(TextFieldValue(prompt, TextRange(prompt.length))) }
    var translationPromptState by remember { mutableStateOf(TextFieldValue(translationPrompt, TextRange(translationPrompt.length))) }
    var insightPromptState by remember { mutableStateOf(TextFieldValue(insightPrompt, TextRange(insightPrompt.length))) }

    LaunchedEffect(prompt) {
        if (promptState.text != prompt) {
            promptState = promptState.copy(text = prompt, selection = TextRange(prompt.length))
        }
    }

    LaunchedEffect(translationPrompt) {
        if (translationPromptState.text != translationPrompt) {
            translationPromptState = translationPromptState.copy(text = translationPrompt, selection = TextRange(translationPrompt.length))
        }
    }

    LaunchedEffect(insightPrompt) {
        if (insightPromptState.text != insightPrompt) {
            insightPromptState = insightPromptState.copy(text = insightPrompt, selection = TextRange(insightPrompt.length))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini AI") },
                navigationIcon = {
                    FeedbackIconButton(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onBack
                    )
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Configure Gemini AI for summarization, translation and insight features.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = {
                                    apiKeyText = it
                                    GeminiApiKeyPreference.put(context, coroutineScope, it)
                                },
                                label = { Text("API Key") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Summarization Model Selection
                            ExposedDropdownMenuBox(
                                expanded = expandedModel,
                                onExpandedChange = { expandedModel = !expandedModel }
                            ) {
                                OutlinedTextField(
                                    value = model,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Summarization Model") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedModel,
                                    onDismissRequest = { expandedModel = false }
                                ) {
                                    val userRequestedModels = GeminiModelPreference.availableModels
                                    
                                    userRequestedModels.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m) },
                                            onClick = {
                                                GeminiModelPreference.put(context, coroutineScope, m)
                                                expandedModel = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                             // Translation Model Selection
                            ExposedDropdownMenuBox(
                                expanded = expandedTranslationModel,
                                onExpandedChange = { expandedTranslationModel = !expandedTranslationModel }
                            ) {
                                OutlinedTextField(
                                    value = translationModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Translation Model") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTranslationModel)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedTranslationModel,
                                    onDismissRequest = { expandedTranslationModel = false }
                                ) {
                                    val userRequestedModels = GeminiModelPreference.availableModels
                                    
                                    userRequestedModels.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m) },
                                            onClick = {
                                                GeminiTranslationModelPreference.put(context, coroutineScope, m)
                                                expandedTranslationModel = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // Insight Model Selection
                            ExposedDropdownMenuBox(
                                expanded = expandedInsightModel,
                                onExpandedChange = { expandedInsightModel = !expandedInsightModel }
                            ) {
                                OutlinedTextField(
                                    value = insightModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Insight Model") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInsightModel)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedInsightModel,
                                    onDismissRequest = { expandedInsightModel = false }
                                ) {
                                    val userRequestedModels = GeminiModelPreference.availableModels
                                    
                                    userRequestedModels.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m) },
                                            onClick = {
                                                GeminiInsightModelPreference.put(context, coroutineScope, m)
                                                expandedInsightModel = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            OutlinedTextField(
                                value = promptState,
                                onValueChange = {
                                    promptState = it
                                    GeminiPromptPreference.put(context, coroutineScope, it.text)
                                },
                                label = { Text("Summarization Prompt") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = translationPromptState,
                                onValueChange = {
                                    translationPromptState = it
                                    GeminiTranslationPromptPreference.put(context, coroutineScope, it.text)
                                },
                                label = { Text("Translation Prompt") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = insightPromptState,
                                onValueChange = {
                                    insightPromptState = it
                                    GeminiInsightPromptPreference.put(context, coroutineScope, it.text)
                                },
                                label = { Text("Insight Prompt") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    )
}
