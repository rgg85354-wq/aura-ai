package com.aura.ai

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.aura.ai.ui.AuraRoot
import com.aura.ai.ui.theme.AuraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Sync history and subscription every time the app comes to the foreground.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val app = auraApp
                if (app.repository.loggedIn.value) {
                    lifecycleScope.launch { runCatching { app.repository.sync() } }
                    app.billing.connect()
                }
            }
        })

        setContent {
            AuraTheme {
                AuraRoot()
            }
        }
    }
}
