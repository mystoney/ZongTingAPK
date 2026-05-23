package com.zongting.zongting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 空 Activity，用于接收 PackageInstaller Session 的安装结果回调。
 * 之所以用 Activity 而非 BroadcastReceiver：
 * 1. 我们的 App 可能在用户确认安装后被系统 kill
 * 2. ActivityManager 能可靠地启动一个 Activity，即使调用者进程已死亡
 * 3. 此 Activity 只需要短暂存活，做完记录后立即 finish()
 */
class InstallResultActivity : android.app.Activity() {
    private val TAG = "InstallResult"

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        val status = intent.getIntExtra("android.content.pm.extra.STATUS", -1)
        val statusMessage = intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE")
        Log.d(TAG, "Session status=$status msg=$statusMessage")

        if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) {
            Log.d(TAG, "Install SUCCESS")
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("install_success", true)
            }
            startActivity(homeIntent)
        } else {
            Log.e(TAG, "Install FAILED: status=$status msg=$statusMessage")
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("install_failed", true)
            }
            startActivity(homeIntent)
        }

        finish()
    }
}
