package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope

sealed class Preference {

    abstract fun put(context: Context, scope: CoroutineScope)
}

fun Preferences.toSettings(): Settings {
    return Settings(
        // Version
        newVersionNumber = NewVersionNumberPreference.fromPreferences(this),
        skipVersionNumber = SkipVersionNumberPreference.fromPreferences(this),
        newVersionPublishDate = NewVersionPublishDatePreference.fromPreferences(this),
        newVersionLog = NewVersionLogPreference.fromPreferences(this),
        newVersionSize = NewVersionSizePreference.fromPreferences(this),
        newVersionDownloadUrl = NewVersionDownloadUrlPreference.fromPreferences(this),

        // Theme
        themeIndex = ThemeIndexPreference.fromPreferences(this),
        customPrimaryColor = CustomPrimaryColorPreference.fromPreferences(this),
        darkTheme = DarkThemePreference.fromPreferences(this),
        amoledDarkTheme = AmoledDarkThemePreference.fromPreferences(this),
        basicFonts = BasicFontsPreference.fromPreferences(this),

        // Feeds page
        feedsFilterBarStyle = FeedsFilterBarStylePreference.fromPreferences(this),
        feedsFilterBarPadding = FeedsFilterBarPaddingPreference.fromPreferences(this),
        feedsFilterBarTonalElevation = FeedsFilterBarTonalElevationPreference.fromPreferences(this),
        feedsTopBarTonalElevation = FeedsTopBarTonalElevationPreference.fromPreferences(this),
        feedsGroupListExpand = FeedsGroupListExpandPreference.fromPreferences(this),
        feedsGroupListTonalElevation = FeedsGroupListTonalElevationPreference.fromPreferences(this),

        // Flow page
        flowFilterBarStyle = FlowFilterBarStylePreference.fromPreferences(this),
        flowFilterBarPadding = FlowFilterBarPaddingPreference.fromPreferences(this),
        flowFilterBarTonalElevation = FlowFilterBarTonalElevationPreference.fromPreferences(this),
        flowTopBarTonalElevation = FlowTopBarTonalElevationPreference.fromPreferences(this),
        flowArticleListFeedIcon = FlowArticleListFeedIconPreference.fromPreferences(this),
        flowArticleListFeedName = FlowArticleListFeedNamePreference.fromPreferences(this),
        flowArticleListImage = FlowArticleListImagePreference.fromPreferences(this),
        flowArticleListDesc = FlowArticleListDescPreference.fromPreferences(this),
        flowArticleListTime = FlowArticleListTimePreference.fromPreferences(this),
        flowArticleListDateStickyHeader = FlowArticleListDateStickyHeaderPreference.fromPreferences(
            this
        ),
        flowArticleListReadIndicator = FlowArticleReadIndicatorPreference.fromPreferences(this),
        flowArticleListTonalElevation = FlowArticleListTonalElevationPreference.fromPreferences(this),
        flowSortUnreadArticles = SortUnreadArticlesPreference.fromPreferences(this),

        // Reading page
        readingRenderer = ReadingRendererPreference.fromPreferences(this),
        readingBoldCharacters = ReadingBoldCharactersPreference.fromPreferences(this),
        readingTheme = ReadingThemePreference.fromPreferences(this),
        readingPageTonalElevation = ReadingPageTonalElevationPreference.fromPreferences(this),
        readingAutoHideToolbar = ReadingAutoHideToolbarPreference.fromPreferences(this),
        readingTextFontSize = ReadingTextFontSizePreference.fromPreferences(this),
        readingTextLineHeight = ReadingTextLineHeightPreference.fromPreferences(this),
        readingLetterSpacing = ReadingTextLetterSpacingPreference.fromPreferences(this),
        readingTextHorizontalPadding = ReadingTextHorizontalPaddingPreference.fromPreferences(this),
        readingTextAlign = ReadingTextAlignPreference.fromPreferences(this),
        readingTextBold = ReadingTextBoldPreference.fromPreferences(this),
        readingTitleAlign = ReadingTitleAlignPreference.fromPreferences(this),
        readingSubheadAlign = ReadingSubheadAlignPreference.fromPreferences(this),
        readingFonts = ReadingFontsPreference.fromPreferences(this),
        readingTitleBold = ReadingTitleBoldPreference.fromPreferences(this),
        readingSubheadBold = ReadingSubheadBoldPreference.fromPreferences(this),
        readingTitleUpperCase = ReadingTitleUpperCasePreference.fromPreferences(this),
        readingSubheadUpperCase = ReadingSubheadUpperCasePreference.fromPreferences(this),
        readingImageHorizontalPadding = ReadingImageHorizontalPaddingPreference.fromPreferences(this),
        readingImageRoundedCorners = ReadingImageRoundedCornersPreference.fromPreferences(this),
        readingImageMaximize = ReadingImageMaximizePreference.fromPreferences(this),

        // Interaction
        initialPage = InitialPagePreference.fromPreferences(this),
        initialFilter = InitialFilterPreference.fromPreferences(this),
        swipeStartAction = SwipeStartActionPreference.fromPreferences(this),
        swipeEndAction = SwipeEndActionPreference.fromPreferences(this),
        markAsReadOnScroll = MarkAsReadOnScrollPreference.fromPreferences(this),
        hideEmptyGroups = HideEmptyGroupsPreference.fromPreferences(this),
        pullToSwitchFeed = PullToLoadNextFeedPreference.fromPreference(this),
        pullToSwitchArticle = PullToSwitchArticlePreference.fromPreference(this),
        openLink = OpenLinkPreference.fromPreferences(this),
        openLinkSpecificBrowser = OpenLinkSpecificBrowserPreference.fromPreferences(this),
        sharedContent = SharedContentPreference.fromPreferences(this),
        typeChoEndpoint = TypeChoEndpointPreference.fromPreferences(this),
        typeChoHomeUrl = TypeChoHomeUrlPreference.fromPreferences(this),
        typeChoUsername = TypeChoUsernamePreference.fromPreferences(this),
        typeChoPassword = TypeChoPasswordPreference.fromPreferences(this),
        typeChoExpirationMinutes = TypeChoExpirationMinutesPreference.fromPreferences(this),
        getNoteApiKey = GetNoteApiKeyPreference.fromPreferences(this),
        getNoteClientId = GetNoteClientIdPreference.fromPreferences(this),
        getNoteTopicId = GetNoteTopicIdPreference.fromPreferences(this),

        // Languages
        languages = LanguagesPreference.fromPreferences(this),

        // AI
        aiProvider = AiProviderPreference.fromPreferences(this),
        geminiApiKey = GeminiApiKeyPreference.fromPreferences(this),
        geminiModel = GeminiModelPreference.fromPreferences(this),
        geminiTranslationModel = GeminiTranslationModelPreference.fromPreferences(this),
        geminiInsightModel = GeminiInsightModelPreference.fromPreferences(this),
        geminiPrompt = GeminiPromptPreference.fromPreferences(this),
        geminiTranslationPrompt = GeminiTranslationPromptPreference.fromPreferences(this),
        geminiInsightPrompt = GeminiInsightPromptPreference.fromPreferences(this),
        codexApiKey = CodexApiKeyPreference.fromPreferences(this),
        codexModel = CodexModelPreference.fromPreferences(this),
        codexTranslationModel = CodexTranslationModelPreference.fromPreferences(this),
        codexInsightModel = CodexInsightModelPreference.fromPreferences(this),
        openAiBaseUrl = OpenAiBaseUrlPreference.fromPreferences(this),

        // TTS
        ttsConfig = TtsConfigPreference.fromPreferences(this),
        ttsReadAiSummaryOnly = TtsReadAiSummaryOnlyPreference.fromPreferences(this),
        ttsSpeechRate = TtsSpeechRatePreference.fromPreferences(this),
    )
}
