package com.supergram.app

import android.app.Application
import com.supergram.app.core.telegram.TelegramClientManager

class SuperGramApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Boots the TDLib client and starts the authorization state machine.
        TelegramClientManager.start()
    }
}
