package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.MainSecurityApp
import com.example.ui.SecurityViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.service.SecurityStateManager

class MainActivity : ComponentActivity() {

    private val viewModel: SecurityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle intent launched from the foreground sensor service
        handleAlarmIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainSecurityApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(intent: Intent?) {
        val launchAlarm = intent?.getBooleanExtra("LAUNCH_ALARM_SCREEN", false) ?: false
        val triggerMode = intent?.getStringExtra("TRIGGER_MODE_EXTRA")

        if (launchAlarm) {
            // Force the app to focus and display the active alarm lock screen
            viewModel.navigateTo("main")
            if (triggerMode != null) {
                SecurityStateManager.triggerAlarm(triggerMode)
            }
        }
    }
}
