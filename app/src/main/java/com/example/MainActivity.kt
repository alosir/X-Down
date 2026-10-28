package com.example

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private lateinit var viewModel: AppViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    viewModel = ViewModelProvider(this)[AppViewModel::class.java]

    handleIntent(intent)

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    setContent {
      val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
      MyApplicationTheme(themeConfig = themeConfig) {
        MainScreen(viewModel = viewModel)
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      try {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null && clipboard.hasPrimaryClip()) {
          val primaryClip = clipboard.primaryClip
          if (primaryClip != null && primaryClip.itemCount > 0) {
            val text = primaryClip.getItemAt(0)?.text?.toString()
            if (!text.isNullOrBlank()) {
              viewModel.checkAndAutoParseClipboard(text)
            }
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: android.content.Intent?) {
    val openTab = intent?.getStringExtra("open_tab")
    if (openTab != null) {
      viewModel.setSelectedTab(openTab)
    }
    if (intent?.getStringExtra("open_changelog") == "true") {
      viewModel.setSelectedTab("about")
      viewModel.requestOpenChangelog()
    }
    // 通知点击：定位到指定下载任务
    intent?.getStringExtra("focus_task")?.let {
      viewModel.setSelectedTab("home")
      viewModel.requestFocusTask(it)
    }
  }
}

