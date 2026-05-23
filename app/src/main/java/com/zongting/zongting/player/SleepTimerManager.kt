package com.zongting.zongting.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.CountDownTimer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zongting.zongting.MainActivity
import com.zongting.zongting.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 定时关闭管理器
 * 倒计时结束后自动暂停播放，支持锁屏通知栏显示剩余时间
 */
object SleepTimerManager {

    private const val CHANNEL_ID = "sleep_timer"
    private const val NOTIFICATION_ID = 9991
    const val ACTION_CANCEL = "com.zongting.zongting.ACTION_CANCEL_TIMER"

    private var countDownTimer: CountDownTimer? = null
    private var notificationManager: NotificationManager? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    /** 启动定时关闭 */
    fun start(context: Context, minutes: Int) {
        cancel()

        val totalMs = minutes * 60 * 1000L
        _isActive.value = true
        _remainingSeconds.value = (minutes * 60).toLong()

        ensureNotificationManager(context)
        ensureChannel(context)

        // 立即显示通知
        showNotification(context, minutes)

        countDownTimer = object : CountDownTimer(totalMs, 60_000L) { // 每分钟更新一次
            override fun onTick(millisUntilFinished: Long) {
                val secs = millisUntilFinished / 1000
                _remainingSeconds.value = secs
                updateNotification(context, (secs / 60).toInt(), (secs % 60).toInt())
            }

            override fun onFinish() {
                _isActive.value = false
                _remainingSeconds.value = 0
                dismissNotification()
                PlayerManager.pause()
            }
        }.start()
    }

    /** 取消定时关闭 */
    fun cancel() {
        countDownTimer?.cancel()
        countDownTimer = null
        _isActive.value = false
        _remainingSeconds.value = 0
        // 不在这里dismissNotification，让调用者处理
    }

    /** 彻底清理（取消并关闭通知） */
    fun cancelWithNotification(context: Context) {
        cancel()
        dismissNotification()
    }

    private fun ensureNotificationManager(context: Context) {
        if (notificationManager == null) {
            notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notificationManager?.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "定时关闭",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "显示定时关闭剩余时间"
                    setShowBadge(false)
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(
        context: Context,
        minutes: Int,
        seconds: Int = 0,
        isOngoing: Boolean = true
    ): android.app.Notification {
        // 取消按钮
        val cancelIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getActivity(
            context, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "定时关闭"
        val timeStr = if (seconds > 0) "${minutes}分${seconds}秒" else "${minutes}分钟后停止播放"
        val content = "剩余 $timeStr"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .addAction(0, "取消", cancelPendingIntent)
            .setContentIntent(cancelPendingIntent)
            .setAutoCancel(false)
            .build()
    }

    private fun showNotification(context: Context, minutes: Int) {
        ensureNotificationManager(context)
        ensureChannel(context)
        val notification = buildNotification(context, minutes)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(context: Context, minutes: Int, seconds: Int) {
        ensureNotificationManager(context)
        ensureChannel(context)
        val notification = buildNotification(context, minutes, seconds)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun dismissNotification() {
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
