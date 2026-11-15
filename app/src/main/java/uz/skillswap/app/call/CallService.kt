package uz.skillswap.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import uz.skillswap.app.MainActivity
import uz.skillswap.app.R
import uz.skillswap.app.SkillSwapApp

/**
 * Keeps a video lesson running while SkillSwap is in the background (e.g. the learner checks their notes):
 * Android only lets an app use the microphone in the background from a foreground service with a visible
 * notification. The notification opens the call again and has a "Leave" button.
 * The service does no work itself; the call lives in [VideoRoomManager]. It stops when the call ends.
 */
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            (application as SkillSwapApp).videoRooms.hangUp()
            stop()
            return START_NOT_STICKY
        }
        val camera = intent?.getBooleanExtra(EXTRA_CAMERA, false) == true
        val microphone = intent?.getBooleanExtra(EXTRA_MIC, false) == true
        val notification = buildNotification(
            title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name),
            text = intent?.getStringExtra(EXTRA_TEXT) ?: "",
            hangUp = intent?.getStringExtra(EXTRA_HANG_UP) ?: "Leave"
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var types = 0
                if (camera) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                if (microphone) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (types == 0) { stop(); return START_NOT_STICKY }       // no camera and no mic: nothing to keep alive
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            // e.g. Android 14 refuses because a permission was just revoked: the call still runs while on screen
            Log.e("VideoRoom", "foreground service refused: ${e.message}")
            stop()
        }
        return START_NOT_STICKY
    }

    /** The app was swiped away from the recent apps: leave the room politely and stop. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as SkillSwapApp).videoRooms.onAppClosed()
        stop()
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String, text: String, hangUp: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.call_channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_CALL, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val leave = PendingIntent.getService(
            this, 1, Intent(this, CallService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(open)
            .addAction(0, hangUp, leave)
            .build()
    }

    companion object {
        private const val CHANNEL = "video_lessons"
        private const val NOTIFICATION_ID = 4107
        private const val ACTION_HANG_UP = "uz.skillswap.app.call.HANG_UP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_HANG_UP = "hang_up"
        private const val EXTRA_CAMERA = "camera"
        private const val EXTRA_MIC = "mic"

        fun start(context: Context, labels: CallLabels, camera: Boolean, microphone: Boolean) {
            val intent = Intent(context, CallService::class.java)
                .putExtra(EXTRA_TITLE, labels.title).putExtra(EXTRA_TEXT, labels.text).putExtra(EXTRA_HANG_UP, labels.hangUp)
                .putExtra(EXTRA_CAMERA, camera).putExtra(EXTRA_MIC, microphone)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: RuntimeException) {
                Log.e("VideoRoom", "could not start the call service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}

/** The real [CallHost]: the foreground service above. */
class AndroidCallHost(private val context: Context) : CallHost {
    override fun onCallActive(labels: CallLabels, camera: Boolean, microphone: Boolean) =
        CallService.start(context.applicationContext, labels, camera, microphone)

    override fun onCallInactive() = CallService.stop(context.applicationContext)
}
