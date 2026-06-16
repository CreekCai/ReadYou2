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
import androidx.compose.material3.TextButton
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
import me.ash.reader.infrastructure.preference.AiProviderPreference
import me.ash.reader.infrastructure.preference.CodexApiKeyPreference
import me.ash.reader.infrastructure.preference.CodexModelPreference
import me.ash.reader.infrastructure.preference.GeminiApiKeyPreference
import me.ash.reader.infrastructure.preference.GeminiModelPreference
import me.ash.reader.infrastructure.preference.GeminiInsightModelPreference
import me.ash.reader.infrastructure.preference.GeminiPromptPreference
import me.ash.reader.infrastructure.preference.GeminiTranslationModelPreference
import me.ash.reader.infrastructure.preference.GeminiTranslationPromptPreference
import me.ash.reader.infrastructure.preference.GeminiInsightPromptPreference
import me.ash.reader.infrastructure.preference.LocalAiProvider
import me.ash.reader.infrastructure.preference.LocalCodexApiKey
import me.ash.reader.infrastructure.preference.LocalCodexModel
import me.ash.reader.infrastructure.preference.LocalGeminiApiKey
import me.ash.reader.infrastructure.preference.LocalGeminiModel
import me.ash.reader.infrastructure.preference.LocalGeminiInsightModel
import me.ash.reader.infrastructure.preference.LocalGeminiPrompt
import me.ash.reader.infrastructure.preference.LocalGeminiTranslationModel
import me.ash.reader.infrastructure.preference.LocalGeminiTranslationPrompt
import me.ash.reader.infrastructure.preference.LocalGeminiInsightPrompt
import me.ash.reader.infrastructure.preference.LocalOpenAiBaseUrl
import me.ash.reader.infrastructure.preference.OpenAiBaseUrlPreference
import me.ash.reader.ui.component.base.FeedbackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiSettingsPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val aiProvider = LocalAiProvider.current
    val apiKey = LocalGeminiApiKey.current
    val codexApiKey = LocalCodexApiKey.current
    val codexModel = LocalCodexModel.current
    val openAiBaseUrl = LocalOpenAiBaseUrl.current
    val model = LocalGeminiModel.current
    val translationModel = LocalGeminiTranslationModel.current
    val insightModel = LocalGeminiInsightModel.current
    val prompt = LocalGeminiPrompt.current
    val translationPrompt = LocalGeminiTranslationPrompt.current
    val insightPrompt = LocalGeminiInsightPrompt.current

    var expandedProvider by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }
    var expandedTranslationModel by remember { mutableStateOf(false) }
    var expandedInsightModel by remember { mutableStateOf(false) }
    
    var apiKeyText by remember { mutableStateOf(apiKey) }
    var codexApiKeyText by remember { mutableStateOf(codexApiKey) }
    var promptState by remember { mutableStateOf(TextFieldValue(prompt, TextRange(prompt.length))) }
    var translationPromptState by remember { mutableStateOf(TextFieldValue(translationPrompt, TextRange(translationPrompt.length))) }
    var insightPromptState by remember { mutableStateOf(TextFieldValue(insightPrompt, TextRange(insightPrompt.length))) }

    LaunchedEffect(prompt) {
        if (promptState.text != prompt) {
            promptState = promptState.copy(text = prompt, selection = TextRange(prompt.length))
        }
    }

    LaunchedEffect(apiKey) {
        if (apiKeyText != apiKey) {
            apiKeyText = apiKey
        }
    }

    LaunchedEffect(codexApiKey) {
        if (codexApiKeyText != codexApiKey) {
            codexApiKeyText = codexApiKey
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
                title = { Text(stringResource(R.string.gemini_ai)) },
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
                                text = stringResource(R.string.ai_settings_page_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            ExposedDropdownMenuBox(
                                expanded = expandedProvider,
                                onExpandedChange = { expandedProvider = !expandedProvider }
                            ) {
                                OutlinedTextField(
                                    value = aiProvider.title,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.ai_provider)) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedProvider,
                                    onDismissRequest = { expandedProvider = false }
                                ) {
                                    AiProviderPreference.values.forEach { provider ->
                                        DropdownMenuItem(
                                            text = { Text(provider.title) },
                                            onClick = {
                                                provider.put(context, coroutineScope)
                                                expandedProvider = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value =
                                    when (aiProvider) {
                                        AiProviderPreference.OpenAI -> codexApiKeyText
                                        else -> apiKeyText
                                    },
                                onValueChange = {
                                    when (aiProvider) {
                                        AiProviderPreference.OpenAI -> {
                                            codexApiKeyText = it
                                            CodexApiKeyPreference.put(context, coroutineScope, it)
                                        }
                                        else -> {
                                            apiKeyText = it
                                            GeminiApiKeyPreference.put(context, coroutineScope, it)
                                        }
                                    }
                                },
                                label = { Text("${aiProvider.title} ${stringResource(R.string.api_key)}") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            if (aiProvider == AiProviderPreference.OpenAI) {
                                OutlinedTextField(
                                    value = codexModel,
                                    onValueChange = {
                                        CodexModelPreference.put(context, coroutineScope, it)
                                    },
                                    label = { Text(stringResource(R.string.openai_model)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = openAiBaseUrl,
                                    onValueChange = {
                                        OpenAiBaseUrlPreference.put(context, coroutineScope, it)
                                    },
                                    label = { Text(stringResource(R.string.openai_base_url)) },
                                    supportingText = { Text(OpenAiBaseUrlPreference.default) },
                                    trailingIcon = {
                                        TextButton(
                                            onClick = {
                                                OpenAiBaseUrlPreference.reset(context, coroutineScope)
                                            }
                                        ) {
                                            Text(stringResource(R.string.reset))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                // Summarization Model Selection
                                ExposedDropdownMenuBox(
                                    expanded = expandedModel,
                                    onExpandedChange = { expandedModel = !expandedModel }
                                ) {
                                    OutlinedTextField(
                                        value = model,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.summarization_model)) },
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
                                        label = { Text(stringResource(R.string.translation_model)) },
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
                                        label = { Text(stringResource(R.string.insight_model)) },
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
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            OutlinedTextField(
                                value = promptState,
                                onValueChange = {
                                    promptState = it
                                    GeminiPromptPreference.put(context, coroutineScope, it.text)
                                },
                                label = { Text(stringResource(R.string.summarization_prompt)) },
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
                                label = { Text(stringResource(R.string.translation_prompt)) },
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
                                label = { Text(stringResource(R.string.insight_prompt)) },
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
