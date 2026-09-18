package me.ash.reader.infrastructure.preference

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object ExplanationPreferences {
    val geminiModel = stringPreferencesKey("geminiExplanationModel")
    val openAiModel = stringPreferencesKey("codexExplanationModel")
    val prompt = stringPreferencesKey("explanationPrompt")
    val search = booleanPreferencesKey("explanationSearch")
    const val defaultPrompt = "请用简体中文解释选中内容在本文中的含义。先给两三句通俗解释；追问时按问题展开。区分作者原意与补充背景，信息不足时明确说明。"
}
