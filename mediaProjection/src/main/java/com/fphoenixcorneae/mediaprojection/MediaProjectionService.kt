package com.fphoenixcorneae.mediaprojection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MediaProjectionService : Service() {

    private var mNotificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotification()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        release()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationIntent = Intent(this, MediaProjectionService::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            val notificationBuilder: NotificationCompat.Builder =
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Media Projection Service")
                    .setContentText("Starting Media Projection Service")
                    .setContentIntent(pendingIntent)
            val notification = notificationBuilder.build()
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = NOTIFICATION_CHANNEL_DESC
            mNotificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
            mNotificationManager?.createNotificationChannel(channel)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun release() {
        mNotificationManager?.cancel(NOTIFICATION_ID)
        mNotificationManager = null

        mMediaProjection?.stop()
        mMediaProjection = null

        stopSelf()
    }

    companion object {
        private const val NOTIFICATION_ID = 666
        private const val NOTIFICATION_CHANNEL_ID = "channel_id"
        private const val NOTIFICATION_CHANNEL_NAME = "channel_name"
        private const val NOTIFICATION_CHANNEL_DESC = "channel_desc"

        private var mMediaProjection: MediaProjection? = null

        /**
         * 获取媒体投影
         */
        fun getMediaProjection(
            manager: MediaProjectionManager?,
            resultCode: Int,
            resultData: Intent,
        ): MediaProjection? {
            return manager?.getMediaProjection(resultCode, resultData).also { mMediaProjection = it }
        }
    }
}