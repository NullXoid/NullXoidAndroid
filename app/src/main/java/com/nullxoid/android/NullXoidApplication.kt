package com.nullxoid.android

import android.app.Application
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
        settingsStore = SettingsStore(this)
        repository = NullXoidRepository(
            settingsStore = settingsStore,
            savedChatAccountKeyProvider = AndroidSavedChatAccountKeyProvider(this)
        )
    }
}
