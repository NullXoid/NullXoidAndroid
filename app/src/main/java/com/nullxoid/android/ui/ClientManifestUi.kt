package com.nullxoid.android.ui

import com.nullxoid.android.data.prefs.SettingsStore

fun availableUpdateSources(state: AppUiState): List<String> {
    val knownSources = listOf(
        SettingsStore.UPDATE_SOURCE_AUTO,
        SettingsStore.UPDATE_SOURCE_FORGEJO,
        SettingsStore.UPDATE_SOURCE_GITHUB
    )
    val channels = state.clientManifest?.updates?.channels
        ?.map { it.trim().lowercase() }
        ?.toSet()
        .orEmpty()
    if (channels.isEmpty()) return listOf(SettingsStore.normalizeUpdateSource(state.updateSource))
    return knownSources.filter { it in channels }.ifEmpty {
        listOf(SettingsStore.normalizeUpdateSource(state.updateSource))
    }
}

fun updateSourceAllowedByManifest(state: AppUiState): String {
    val available = availableUpdateSources(state)
    val current = SettingsStore.normalizeUpdateSource(state.updateSource)
    if (current in available) return current
    val recommended = SettingsStore.normalizeUpdateSource(state.clientManifest?.updates?.recommendedChannel)
    return if (recommended in available) recommended else available.first()
}
