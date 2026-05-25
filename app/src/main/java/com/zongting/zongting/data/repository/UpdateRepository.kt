package com.zongting.zongting.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import androidx.core.content.FileProvider
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zongting.zongting.MainActivity
import com.zongting.zongting.R as appR
import com.zongting.zongting.data.model.VersionInfo
import com.zongting.zongting.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

private const val ACTION_INSTALL_COMPLETE = "com.zongting.zongting.INSTALL_COMPLETE"
private const val NOTIFICATION_CHANNEL_ID = "zongting_install"
private const val NOTIFICATION_ID = 1001
private const val FOREGROUND_SERVICE_ID = 1002

// Events exposed to UI for update dialog
sealed class UpdateEvent {
    object Checking : UpdateEvent()
    data class UpdateAvailable(val versionInfo: VersionInfo, val isTestChannel: Boolean = false) : UpdateEvent()
    data class Downloading(val progress: Int, val isTestChannel: Boolean = false) : UpdateEvent()
    data class Downloaded(val apkFile: File, val isTestChannel: Boolean = false) : UpdateEvent()
    data class Error(val message: String) : UpdateEvent()
}

// 顶部进度条的状态
enum class UpdatePhase {
    Idle,
    UpdateAvailable,
    Downloading,
    Downloaded,
}

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingInstallManager: PendingInstallManager
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _updateEvent = MutableStateFlow<UpdateEvent?>(null)
    val updateEvent: StateFlow<UpdateEvent?> = _updateEvent

    private val _updatePhase = MutableStateFlow(UpdatePhase.Idle)
    val updatePhase: StateFlow<UpdatePhase> = _updatePhase

    private var pendingVersionInfo: VersionInfo? = null
    private var isDownloading = false
    private var downloadedApkFile: File? = null

    private val isTestChannel: Boolean
        get() = BuildConfig.UPDATE_CHANNEL == "test"

    private val versionJsonUrl: String
        get() = BuildConfig.VERSION_JSON_URL

    companion object {
        private const val TAG = "UpdateRepository"
        private const val UPDATE_SERVER_BASE = "http://172.16.1.93:8080"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_PENDING_SESSION_ID = "pending_session_id"
        private const val KEY_PENDING_VERSION_CODE = "pending_version_code"
        private const val KEY_LAST_KNOWN_VERSION = "last_known_version"
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── 启动时检查上次安装是否已完成 ─────────────────────────
    fun scheduleBackgroundUpdateCheck(delayMs: Long = 3_000) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            _updateEvent.value = UpdateEvent.Checking
            _updatePhase.value = UpdatePhase.Idle
            checkPendingSession()
            checkJustUpdated()
            kotlinx.coroutines.delay(delayMs)
            performBackgroundCheck()
        }
    }

    // 检测是否刚完成安装（版本已变化但轮询服务被杀）
    private fun checkJustUpdated() {
        val currentVersion = getCurrentVersionCode()
        val lastKnown = prefs.getInt(KEY_LAST_KNOWN_VERSION, 0)
        Log.d(TAG, "checkJustUpdated: current=$currentVersion, lastKnown=$lastKnown")
        if (currentVersion > lastKnown && lastKnown > 0) {
            Log.d(TAG, "App was just updated! Showing success notification")
            showInstallSuccessNotification()
        }
        // 更新已知版本
        prefs.edit().putInt(KEY_LAST_KNOWN_VERSION, currentVersion).apply()
    }

    private fun showInstallSuccessNotification() {
        val channelId = "zongting_install"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "纵听安装", NotificationManager.IMPORTANCE_HIGH)
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("安装完成")
            .setContentText("纵听已更新到最新版本，点击打开")
            .setSmallIcon(appR.drawable.ic_notification_install)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 0,
                Intent(context, com.zongting.zongting.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(1003, notification)
    }

    private fun checkPendingSession() {
        val sessionId = prefs.getInt(KEY_PENDING_SESSION_ID, -1)
        if (sessionId == -1) return
        Log.d(TAG, "checkPendingSession: sessionId=$sessionId")

        try {
            val packageInstaller = context.packageManager.packageInstaller
            val sessionInfo = packageInstaller.getSessionInfo(sessionId)
            if (sessionInfo == null || !sessionInfo.isActive) {
                Log.d(TAG, "checkPendingSession: session completed or invalid, clearing")
                clearPendingSession()
            } else {
                Log.d(TAG, "checkPendingSession: session still active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkPendingSession failed: ${e.message}")
            clearPendingSession()
        }
    }

    private fun clearPendingSession() {
        prefs.edit()
            .remove(KEY_PENDING_SESSION_ID)
            .remove(KEY_PENDING_VERSION_CODE)
            .apply()
    }

    // ── 更新检测 ───────────────────────────────────────────
    private suspend fun performBackgroundCheck() = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for update at $versionJsonUrl")
            val request = okhttp3.Request.Builder().url(versionJsonUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Update check failed: ${response.code}")
                _updateEvent.value = UpdateEvent.Error("检查更新失败: ${response.code}")
                return@withContext
            }

            val body = response.body?.string() ?: ""
            val json = com.google.gson.JsonParser().parse(body).asJsonObject
            val remoteVersionCode = json.get("versionCode").asInt
            val currentCode = getCurrentVersionCode()

            Log.d(TAG, "Remote=$remoteVersionCode, Current=$currentCode")

            if (remoteVersionCode <= currentCode) {
                _updateEvent.value = null
                return@withContext
            }

            val apkUrl = json.get("apkUrl")?.asString
                ?: "$UPDATE_SERVER_BASE/zongting-latest.apk"

            pendingVersionInfo = VersionInfo(
                versionCode = remoteVersionCode,
                versionName = json.get("versionName")?.asString ?: "",
                apkUrl = apkUrl,
                releaseNotes = json.get("releaseNotes")?.asString ?: json.get("updateContent")?.asString ?: "",
                forceUpdate = json.get("forceUpdate")?.asBoolean ?: false,
                channel = json.get("channel")?.asString ?: ""
            )

            _updatePhase.value = UpdatePhase.UpdateAvailable
            _updateEvent.value = UpdateEvent.UpdateAvailable(
                pendingVersionInfo!!,
                isTestChannel = isTestChannel
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check error: ${e.message}")
            _updateEvent.value = UpdateEvent.Error("检查更新失败: ${e.message}")
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } catch (e: Exception) { 0 }
    }

    // ── 用户操作 ─────────────────────────────────────────────
    fun confirmDownload() {
        pendingVersionInfo?.let { startDownload(it) }
    }

    fun dismissUpdate() {
        pendingVersionInfo = null
        _updatePhase.value = UpdatePhase.Idle
        _updateEvent.value = null
    }

    fun confirmInstall() {
        if (_updatePhase.value != UpdatePhase.Downloaded) return
        downloadedApkFile?.let { apkFile ->
            // ── 关键验证：安装前核对 APK 版本号 ──
            try {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                    ?: throw Exception("无法解析 APK 文件")
                // 必须设置 sourceDir，PackageManager 才能读取 APK 内容
                pkgInfo.applicationInfo?.apply {
                    sourceDir = apkFile.absolutePath
                    publicSourceDir = apkFile.absolutePath
                }
                val apkVersionCode = pkgInfo.versionCode
                val expectedVersionCode = pendingVersionInfo?.versionCode ?: 0
                Log.d(TAG, "APK verification: file_version=$apkVersionCode, expected=$expectedVersionCode")
                if (apkVersionCode != expectedVersionCode) {
                    Log.e(TAG, "APK version mismatch! File=$apkVersionCode, Expected=$expectedVersionCode")
                    _updateEvent.value = UpdateEvent.Error("APK 版本号不匹配(${apkVersionCode}≠${expectedVersionCode})，请重新下载")
                    _updatePhase.value = UpdatePhase.Idle
                    downloadedApkFile = null
                    apkFile.delete()
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "APK verification failed: ${e.message}")
                _updateEvent.value = UpdateEvent.Error("APK 文件损坏，请重新下载")
                _updatePhase.value = UpdatePhase.Idle
                downloadedApkFile = null
                apkFile.delete()
                return
            }

            pendingInstallManager.clearPendingApk()
            installApk(apkFile)
        }
    }

    fun deferInstall() {
        downloadedApkFile?.let { apkFile ->
            pendingInstallManager.savePendingApk(apkFile.absolutePath)
        }
        downloadedApkFile = null
        pendingVersionInfo = null
        _updateEvent.value = null
        _updatePhase.value = UpdatePhase.Idle
    }

    // ── 下载 ─────────────────────────────────────────────────
    private fun startDownload(info: VersionInfo) {
        isDownloading = true
        _updatePhase.value = UpdatePhase.Downloading
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            downloadApk(info.apkUrl) { progress ->
                _updateEvent.value = UpdateEvent.Downloading(progress, isTestChannel = isTestChannel)
            }.onSuccess { apkFile ->
                isDownloading = false
                downloadedApkFile = apkFile
                _updatePhase.value = UpdatePhase.Downloaded
                _updateEvent.value = UpdateEvent.Downloaded(apkFile, isTestChannel = isTestChannel)
            }.onFailure { e ->
                isDownloading = false
                Log.e(TAG, "Download failed: ${e.message}")
                _updatePhase.value = UpdatePhase.Idle
                _updateEvent.value = UpdateEvent.Error("下载失败: ${e.message}")
            }
        }
    }

    private suspend fun downloadApk(url: String, onProgress: (Int) -> Unit): Result<java.io.File> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                val totalBytes = body.contentLength()
                val updateDir = java.io.File(context.cacheDir, "updates").also { it.mkdirs() }
                val apkFile = java.io.File(updateDir, "zongting-update.apk")

                var downloadedBytes = 0L
                var lastProgress = 0

                body.byteStream().use { input ->
                    java.io.FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            if (bytes > 0) {
                                output.write(buffer, 0, bytes)
                                downloadedBytes += bytes
                                if (totalBytes > 0) {
                                    val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                    if (progress != lastProgress) {
                                        lastProgress = progress
                                        onProgress(progress)
                                    }
                                }
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                Log.d(TAG, "APK downloaded: ${apkFile.absolutePath}, size=${apkFile.length()}")
                Result.success(apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // ── 安装 ─────────────────────────────────────────────────
    private fun installApk(apkFile: java.io.File) {
        try {
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } catch (e: Exception) { 0 }
            // 记录当前版本，安装后 app 重启时检测版本变化判断安装成功
            prefs.edit().putInt(KEY_LAST_KNOWN_VERSION, currentVersion).apply()

            // 启动前台服务，保持进程存活，轮询检测安装结果
            val serviceIntent = android.content.Intent(context, InstallForegroundService::class.java).apply {
                putExtra("expected_version", currentVersion + 1)
                putExtra("apk_path", apkFile.absolutePath)
            }
            context.startForegroundService(serviceIntent)

            // 启动安装流程（ACTION_VIEW 触发系统 PackageInstaller 确认界面）
            InstallLauncher.launchInstall(context, apkFile, currentVersion)

            // 立即清除更新状态（前台服务会接管通知）
            _updatePhase.value = UpdatePhase.Idle
            _updateEvent.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}", e)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 安装启动器：启动前台服务，保持进程存活，轮询检测安装结果
// ─────────────────────────────────────────────────────────────
object InstallLauncher {
    private const val TAG = "InstallLauncher"

    fun launchInstall(context: Context, apkFile: java.io.File, currentVersionCode: Int) {
        Log.d(TAG, "launchInstall: ${apkFile.absolutePath}, exists=${apkFile.exists()}, size=${apkFile.length()}")

        val packageManager = context.packageManager

        // ── 权限检查 ───────────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Log.d(TAG, "No REQUEST_INSTALL_PACKAGES permission, requesting via Settings")
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(settingsIntent)
                } catch (e: Exception) {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }
                return
            } else {
                Log.d(TAG, "REQUEST_INSTALL_PACKAGES permission OK")
            }
        }

        // ── 方案一（主）：复制到 /data/local/tmp/ + file:// URI + 显式 ComponentName ──
        // 解决 Nemu Store 拦截 content:// URI 的问题
        // /data/local/tmp/ 是系统级目录，PackageInstaller 可直接读取
        try {
            val installDir = java.io.File("/data/local/tmp")
            if (!installDir.exists() || !installDir.canWrite()) {
                // fallback: 尝试 app 外部存储目录
                val fallbackDir = java.io.File(context.getExternalFilesDir(null), "apk_install")
                fallbackDir.mkdirs()
                val destFile = java.io.File(fallbackDir, "zongting-update.apk")
                apkFile.copyTo(destFile, overwrite = true)
                // 确保全局可读
                Runtime.getRuntime().exec(arrayOf("chmod", "644", destFile.absolutePath)).waitFor()
                launchWithFileUri(context, destFile)
                Log.d(TAG, "Using fallback dir: ${destFile.absolutePath}")
                return
            }

            val destFile = java.io.File(installDir, "zongting-update.apk")
            apkFile.copyTo(destFile, overwrite = true)
            // 确保全局可读（system_server 进程需要能读）
            Runtime.getRuntime().exec(arrayOf("chmod", "644", destFile.absolutePath)).waitFor()
            Log.d(TAG, "APK copied to: ${destFile.absolutePath}")

            launchWithFileUri(context, destFile)
            return
        } catch (e: Exception) {
            Log.e(TAG, "file:// approach failed: ${e.message}", e)
        }

        // ── 方案二（备）：FileProvider content:// + am start 显式组件 ──
        try {
            val authority = "${context.packageName}.updatefileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
            Log.d(TAG, "FileProvider URI: $contentUri")
            launchWithContentUri(context, contentUri)
            return
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider approach failed: ${e.message}", e)
        }

        // ── 方案三（最后备）：pm install -r（root 设备）───
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm install -r ${apkFile.absolutePath}"))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            Log.d(TAG, "pm install exitCode=$exitCode output=$output error=$error")
            if (exitCode == 0) {
                Log.d(TAG, "Silent install SUCCESS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "su pm install failed: ${e.message}", e)
        }
    }

    /**
     * 使用 file:// URI + 显式 ComponentName 启动系统 PackageInstaller
     * 显式指定 com.android.packageinstaller 的 activity，绕过 Nemu Store 拦截
     */
    private fun launchWithFileUri(context: Context, apkFile: java.io.File) {
        val uri = Uri.fromFile(apkFile)
        Log.d(TAG, "launchWithFileUri: $uri")

        // 优先尝试直接指定 PackageInstaller 组件（绕过 Nemu Store 拦截）
        val components = listOf(
            // 标准 AOSP PackageInstaller
            android.content.ComponentName("com.android.packageinstaller",
                "com.android.packageinstaller.PackageInstallerActivity"),
            // Nemu/EMUIX 兼容
            android.content.ComponentName("com.android.packageinstaller",
                "com.android.packageinstaller.InstallStart")
        )

        for (component in components) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    this.component = component
                }
                Log.d(TAG, "Trying component: ${component.flattenToShortString()}")
                context.startActivity(intent)
                Log.d(TAG, "startActivity with explicit component succeeded!")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Component $component failed: ${e.message}")
            }
        }

        // fallback: 使用 ACTION_VIEW + MATCH_DEFAULT_ONLY + 清除 Nemu Store 默认处理
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // 不设置 component，依赖系统解析
            }
            context.startActivity(intent)
            Log.d(TAG, "startActivity (no explicit component) succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "All install approaches failed: ${e.message}", e)
        }
    }

    /**
     * 使用 content:// URI + am start 方式启动
     * 在需要跨进程传递 APK 时使用
     */
    private fun launchWithContentUri(context: Context, contentUri: Uri) {
        Log.d(TAG, "launchWithContentUri: $contentUri")

        // 使用 am start 显式指定包名，绕过 Nemu Store
        try {
            val apkPath = contentUri.path ?: ""
            val command = "am start -n com.android.packageinstaller/com.android.packageinstaller.PackageInstallerActivity " +
                    "-a android.intent.action.VIEW " +
                    "-d '$apkPath' " +
                    "-t application/vnd.android.package-archive " +
                    "--grant-read-uri-permission " +
                    "--activity-clear-top"

            Log.d(TAG, "Running: $command")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            Log.d(TAG, "am start exitCode=$exitCode output=$output error=$error")
            if (exitCode == 0) return
        } catch (e: Exception) {
            Log.e(TAG, "am start failed: ${e.message}", e)
        }

        // fallback: 常规 Intent
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

// ─────────────────────────────────────────────────────────────
// 前台服务：保持进程存活，轮询检测安装结果
// ─────────────────────────────────────────────────────────────
class InstallForegroundService : Service() {
    private val TAG = "InstallFgService"
    private var pollHandler: android.os.Handler? = null
    private var pollRunnable: Runnable? = null
    private var expectedVersion: Int = -1
    private var pollCount = 0
    private val maxPollCount = 90  // 最多轮询 90 次（3 分钟）

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        expectedVersion = intent?.getIntExtra("expected_version", -1) ?: -1
        Log.d(TAG, "onStartCommand: expectedVersion=$expectedVersion")

        val notification = NotificationCompat.Builder(this, "zongting_install")
            .setContentTitle("纵听安装中")
            .setContentText("请在弹出的系统界面中确认安装...")
            .setSmallIcon(appR.drawable.ic_notification_install)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        Log.d(TAG, "onStartCommand: calling startForeground")
        try {
            startForeground(1002, notification)
            Log.d(TAG, "onStartCommand: startForeground success")
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand: startForeground failed", e)
        }

        // 开始轮询检测安装结果
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        pollHandler = android.os.Handler(mainLooper)
        pollRunnable = object : Runnable {
            override fun run() {
                pollCount++
                Log.d(TAG, "polling check #$pollCount (version=$expectedVersion)")

                // 尝试获取新版本信息
                val newVersion = try {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                } catch (e: Exception) { -1 }

                Log.d(TAG, "current version=$newVersion, expected=$expectedVersion")

                if (newVersion >= expectedVersion) {
                    // 安装成功！
                    Log.d(TAG, "Install SUCCESS (polled version=$newVersion)")
                    updateNotification("安装成功！点击打开纵听", isError = false)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                    return
                }

                if (pollCount >= maxPollCount) {
                    // 超时
                    Log.w(TAG, "Polling timeout, install may have failed")
                    updateNotification("安装超时，请重试", isError = true)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                    return
                }

                // 继续轮询（每 2 秒）
                pollHandler?.postDelayed(this, 2000)
            }
        }
        // 立即执行第一次检查（延迟 1 秒让系统安装界面先弹出来）
        pollHandler?.postDelayed(pollRunnable!!, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        pollRunnable?.let { pollHandler?.removeCallbacks(it) }
        Log.d(TAG, "onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "zongting_install",
                "纵听安装",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "安装进度通知" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(text: String, isError: Boolean) {
        val notification = NotificationCompat.Builder(this, "zongting_install")
            .setContentTitle(if (isError) "安装失败" else "安装完成")
            .setContentText(text)
            .setSmallIcon(if (isError) appR.drawable.ic_notification_install else appR.drawable.ic_notification_install)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0,
                Intent(this, com.zongting.zongting.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1002, notification)
    }
}
