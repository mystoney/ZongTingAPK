package com.zongting.zongting.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理"稍后安装"的APK：
 * - 下载完成后用户选择"稍后"时，保存APK路径到SharedPreferences
 * - 下次启动时检测并自动安装
 */
@Singleton
class PendingInstallManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PendingInstallMgr"
        private const val PREFS_NAME = "zongting_prefs"
        private const val KEY_PENDING_APK_PATH = "pending_apk_path"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 保存待安装的APK路径（用户选择"稍后安装"时调用） */
    fun savePendingApk(apkPath: String) {
        Log.d(TAG, "Saving pending APK: $apkPath")
        prefs.edit().putString(KEY_PENDING_APK_PATH, apkPath).apply()
    }

    /** 获取待安装APK路径，若无则返回null */
    fun getPendingApkPath(): String? {
        return prefs.getString(KEY_PENDING_APK_PATH, null)
    }

    /** 清除待安装APK记录（安装完成后或用户取消时调用） */
    fun clearPendingApk() {
        Log.d(TAG, "Clearing pending APK")
        prefs.edit().remove(KEY_PENDING_APK_PATH).apply()
    }

    /** 检查是否存在待安装APK，且文件存在 */
    fun hasPendingApk(): Boolean {
        val path = getPendingApkPath() ?: return false
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    /**
     * 执行待安装APK的安装（启动系统安装界面）
     * @return true if installation intent was launched
     */
    fun installPendingApk(): Boolean {
        val path = getPendingApkPath() ?: run {
            Log.d(TAG, "No pending APK to install")
            return false
        }
        val apkFile = File(path)
        if (!apkFile.exists()) {
            Log.w(TAG, "Pending APK file not found: $path, clearing")
            clearPendingApk()
            return false
        }

        Log.d(TAG, "Installing pending APK: $path")
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.updatefileprovider",
                apkFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install pending APK: ${e.message}")
            clearPendingApk()
            false
        }
    }
}
