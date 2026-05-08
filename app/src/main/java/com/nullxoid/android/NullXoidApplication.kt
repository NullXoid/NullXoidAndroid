package com.nullxoid.android

import android.app.Application
import com.nullxoid.android.data.api.HttpClient
import com.nullxoid.android.data.e2ee.AndroidSavedChatAccountKeyProvider
import com.nullxoid.android.data.prefs.SettingsStore
import com.nullxoid.android.data.repo.NullXoidRepository

class NullXoidApplication : Application() {

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var repository: NullXoidRepository
        private set

    override fun onCreate() {
        super.onCreate()
        HttpClient.init(this)
        settingsStore = SettingsStore(this)
        repository = NullXoidRepository(
            settingsStore = settingsStore,
            savedChatAccountKeyProvider = AndroidSavedChatAccountKeyProvider(this)
        )
    }
}
