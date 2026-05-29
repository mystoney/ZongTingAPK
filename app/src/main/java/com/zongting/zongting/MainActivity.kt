@file:OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zongting.zongting

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zongting.zongting.data.repository.PendingInstallManager
import com.zongting.zongting.player.PlaybackService
import com.zongting.zongting.player.SleepTimerManager
import com.zongting.zongting.ui.MainNavigation
import com.zongting.zongting.ui.MainViewModel
import com.zongting.zongting.ui.SplashScreen
import com.zongting.zongting.ui.UpdateRepositoryEntryPoint
import com.zongting.zongting.ui.theme.ZongTingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private var mediaController: MediaController? = null

    private var showSplash by mutableStateOf(true)
    // 用户是否明确选择了"本次跳过"
    private var userExplicitlySkipped by mutableStateOf(false)
    // 是否已经触发过更新检测（防止 onResume 重复触发）
    private var updateCheckTriggered by mutableStateOf(false)

    // 用于在 onResume 中取消尚未执行的 scheduleUpdateCheck
    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdateCheckRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 处理通知栏定时关闭取消按钮
        if (intent?.action == SleepTimerManager.ACTION_CANCEL) {
            SleepTimerManager.cancelWithNotification(this)
        }

        // 检查是否有待安装的APK（上次选择"稍后安装"）
        checkAndInstallPendingApk()

        // 初始化 MediaController
        initializeMediaController()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            ZongTingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showPermissionDialog by remember { mutableStateOf(false) }

                    if (showSplash) {
                        SplashScreen(
                            onNavigateToMain = {
                                showSplash = false
                                // 检查是否需要请求安装权限
                                if (!packageManager.canRequestPackageInstalls()) {
                                    showPermissionDialog = true
                                } else {
                                    scheduleUpdateCheck()
                                }
                            }
                        )
                    } else {
                        MainNavigation(windowSizeClass = windowSizeClass)
                    }

                    // 安装权限对话框
                    if (showPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showPermissionDialog = false
                                userExplicitlySkipped = true
                                scheduleUpdateCheck()
                            },
                            title = {
                                Text(
                                    text = "需要安装权限",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Text(
                                    text = "纵听需要「安装未知应用」权限才能自动更新。\n\n是否前往设置开启？\n\n开启后每次启动将自动检测并提示更新。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showPermissionDialog = false
                                        openInstallPermissionSettings()
                                    }
                                ) {
                                    Text("去设置")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showPermissionDialog = false
                                        userExplicitlySkipped = true
                                        scheduleUpdateCheck()
                                    }
                                ) {
                                    Text("本次跳过")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun openInstallPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                // Ignore
            }
        }
        // 不在这里 scheduleUpdateCheck，等 onResume 从设置回来时处理
    }

    private fun scheduleUpdateCheck() {
        if (updateCheckTriggered) return
        updateCheckTriggered = true

        pendingUpdateCheckRunnable = Runnable {
            if (!userExplicitlySkipped) {
                scheduleBackgroundUpdateCheck()
            }
        }
        handler.postDelayed(pendingUpdateCheckRunnable!!, 3_000)
    }

    override fun onResume() {
        super.onResume()
        // 从设置页回来：检查权限是否已授予，若是则立即触发更新检测
        if (packageManager.canRequestPackageInstalls() && !updateCheckTriggered) {
            scheduleUpdateCheck()
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface PendingInstallManagerEntryPoint {
        fun pendingInstallManager(): PendingInstallManager
    }

    private fun scheduleBackgroundUpdateCheck() {
        val appContext = applicationContext
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            appContext,
            UpdateRepositoryEntryPoint::class.java
        )
        entryPoint.updateRepository().scheduleBackgroundUpdateCheck()
    }

    private fun checkAndInstallPendingApk() {
        val appContext = applicationContext
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            appContext,
            PendingInstallManagerEntryPoint::class.java
        )
        val manager = entryPoint.pendingInstallManager()
        if (manager.hasPendingApk()) {
            manager.clearPendingApk()
            handler.postDelayed({
                manager.installPendingApk()
            }, 3_000)
        }
    }

    private fun initializeMediaController() {
        try {
            val sessionToken = SessionToken(
                this,
                ComponentName(this, PlaybackService::class.java)
            )
            val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
            controllerFuture.addListener(
                {
                    try {
                        mediaController = controllerFuture.get()
                    } catch (e: Exception) {
                        // Service not ready yet, will retry in onStart
                    }
                },
                MoreExecutors.directExecutor()
            )
        } catch (e: Exception) {
            // Silently skip if service not available
        }
    }

    override fun onStart() {
        super.onStart()
        startService(Intent(this, PlaybackService::class.java))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (intent.action == SleepTimerManager.ACTION_CANCEL) {
            SleepTimerManager.cancelWithNotification(this)
        }
    }

    override fun onPause() {
        super.onPause()
        mainViewModel.savePlaybackState()
    }

    override fun onDestroy() {
        mediaController?.release()
        pendingUpdateCheckRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}
