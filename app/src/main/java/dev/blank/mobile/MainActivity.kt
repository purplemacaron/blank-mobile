package dev.blank.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.blank.mobile.agent.AgentRunService
import dev.blank.mobile.ui.AppRoot
import dev.blank.mobile.ui.theme.BlankMobileTheme

object AppState {
    var foreground: Boolean = true
}

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            BlankMobileTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppState.foreground = true
        AgentRunService.cancelAttention(this)
    }

    override fun onStop() {
        super.onStop()
        AppState.foreground = false
        AgentRunService.pendingAttention?.let { (title, text) ->
            AgentRunService.notifyAttention(this, title, text)
        }
    }
}