package com.zongting.zongting

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.media3.session.MediaController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zongting.zongting.player.PlaybackService
import com.zongting.zongting.ui.MainNavigation
import com.zongting.zongting.ui.SplashScreen
import com.zongting.zongting.ui.theme.ZongTingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null

    private var showSplash by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化 MediaController
        initializeMediaController()

        setContent {
            ZongTingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        SplashScreen(
                            onNavigateToMain = { showSplash = false }
                        )
                    } else {
                        MainNavigation()
                    }
                }
            }
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
        // 启动播放服务
        startService(Intent(this, PlaybackService::class.java))
    }

    override fun onDestroy() {
        mediaController?.release()
        super.onDestroy()
    }
}
