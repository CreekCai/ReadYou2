package me.ash.reader.ui.page.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.infrastructure.preference.LocalTypeChoEndpoint
import me.ash.reader.infrastructure.preference.LocalTypeChoHomeUrl
import me.ash.reader.infrastructure.preference.LocalTypeChoPassword
import me.ash.reader.infrastructure.preference.LocalTypeChoUsername
import me.ash.reader.infrastructure.preference.SharedContentPreference
import me.ash.reader.infrastructure.preference.TypeChoEndpointPreference
import me.ash.reader.infrastructure.preference.TypeChoHomeUrlPreference
import me.ash.reader.infrastructure.preference.TypeChoPasswordPreference
import me.ash.reader.infrastructure.preference.TypeChoUsernamePreference
import me.ash.reader.ui.component.base.FeedbackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSettingsPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedContent = LocalSharedContent.current
    val typeChoEndpoint = LocalTypeChoEndpoint.current
    val typeChoHomeUrl = LocalTypeChoHomeUrl.current
    val typeChoUsername = LocalTypeChoUsername.current
    val typeChoPassword = LocalTypeChoPassword.current

    var expandedMode by remember { mutableStateOf(false) }
    var endpointText by remember(typeChoEndpoint) { mutableStateOf(typeChoEndpoint) }
    var homeUrlText by remember(typeChoHomeUrl) { mutableStateOf(typeChoHomeUrl) }
    var usernameText by remember(typeChoUsername) { mutableStateOf(typeChoUsername) }
    var passwordText by remember(typeChoPassword) { mutableStateOf(typeChoPassword) }

    LaunchedEffect(typeChoEndpoint) {
        if (endpointText != typeChoEndpoint) endpointText = typeChoEndpoint
    }
    LaunchedEffect(typeChoHomeUrl) {
        if (homeUrlText != typeChoHomeUrl) homeUrlText = typeChoHomeUrl
    }
    LaunchedEffect(typeChoUsername) {
        if (usernameText != typeChoUsername) usernameText = typeChoUsername
    }
    LaunchedEffect(typeChoPassword) {
        if (passwordText != typeChoPassword) passwordText = typeChoPassword
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_settings)) },
                navigationIcon = {
                    FeedbackIconButton(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onBack,
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedMode,
                        onExpandedChange = { expandedMode = !expandedMode },
                    ) {
                        OutlinedTextField(
                            value = sharedContent.toDesc(context),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.share_action)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMode)
                            },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMode,
                            onDismissRequest = { expandedMode = false },
                        ) {
                            SharedContentPreference.values.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.toDesc(context)) },
                                    onClick = {
                                        mode.put(context, coroutineScope)
                                        expandedMode = false
                                    },
                                )
                            }
                        }
                    }

                    if (sharedContent == SharedContentPreference.TypeCho) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = endpointText,
                            onValueChange = {
                                endpointText = it
                                TypeChoEndpointPreference.put(context, coroutineScope, it)
                            },
                            label = { Text(stringResource(R.string.typecho_endpoint)) },
                            supportingText = { Text(stringResource(R.string.typecho_endpoint_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = homeUrlText,
                            onValueChange = {
                                homeUrlText = it
                                TypeChoHomeUrlPreference.put(context, coroutineScope, it)
                            },
                            label = { Text(stringResource(R.string.typecho_home_url)) },
                            supportingText = { Text("https://example.com") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = usernameText,
                            onValueChange = {
                                usernameText = it
                                TypeChoUsernamePreference.put(context, coroutineScope, it)
                            },
                            label = { Text(stringResource(R.string.typecho_username)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = passwordText,
                            onValueChange = {
                                passwordText = it
                                TypeChoPasswordPreference.put(context, coroutineScope, it)
                            },
                            label = { Text(stringResource(R.string.typecho_password)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
