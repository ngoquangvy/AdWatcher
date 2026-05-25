package com.adwatcher.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.adwatcher.app.analyzer.AppAnalyzer
import com.adwatcher.app.analyzer.AppRiskInfo
import com.adwatcher.app.data.AppDatabase
import com.adwatcher.app.data.PopupLog
import com.adwatcher.app.service.AdDetectionService
import com.adwatcher.app.ui.AdWatcherTheme
import com.adwatcher.app.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    
    // UI state states
    private var isAccessibilityEnabledState = mutableStateOf(false)
    private var isScanningState = mutableStateOf(false)
    private val appsListState = mutableStateListOf<AppRiskInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initial permission check
        checkAccessibilityStatus()

        // Scan apps on startup
        triggerAppScan()

        setContent {
            AdWatcherTheme {
                // Collect Room database logs dynamically in real-time as a Compose State flow
                val logsList by database.popupLogDao().getAllLogsFlow().collectAsState(initial = emptyList())
                val isAccessibilityEnabled by remember { isAccessibilityEnabledState }
                val isScanning by remember { isScanningState }

                MainScreen(
                    logs = logsList,
                    onClearLogs = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            database.popupLogDao().clearLogs()
                        }
                    },
                    appsList = appsListState,
                    isScanning = isScanning,
                    onRefreshScan = { triggerAppScan() },
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onRequestAccessibilityPermission = { openAccessibilitySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume();
        // Check permission state again in case user comes back from settings
        checkAccessibilityStatus()
    }

    private fun checkAccessibilityStatus() {
        isAccessibilityEnabledState.value = isAccessibilityServiceEnabled()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val serviceInfo = enabledService.resolveInfo.serviceInfo
            if (serviceInfo.packageName == packageName && serviceInfo.name == AdDetectionService::class.java.name) {
                return true
            }
        }
        return false
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerAppScan() {
        if (isScanningState.value) return
        
        isScanningState.value = true
        lifecycleScope.launch(Dispatchers.Default) {
            val analyzer = AppAnalyzer(applicationContext)
            val results = analyzer.scanInstalledApps()
            
            withContext(Dispatchers.Main) {
                appsListState.clear()
                appsListState.addAll(results)
                isScanningState.value = false
            }
        }
    }
}
