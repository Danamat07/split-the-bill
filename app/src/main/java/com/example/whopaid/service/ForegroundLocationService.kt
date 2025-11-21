package com.example.whopaid.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.whopaid.MainActivity
import com.example.whopaid.repo.LocationRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ForegroundLocationService : Service() {

    companion object {
        const val CHANNEL_ID = "who_paid_location_channel"
        const val NOTIF_ID = 1001

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (service.service.className == ForegroundLocationService::class.java.name) {
                    return true
                }
            }
            return false
        }
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val repo = LocationRepository()

    private var groupId: String? = null
    private var userId: String? = null
    private var userName: String? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create().apply {
            interval = 8000L
            fastestInterval = 4000L
            smallestDisplacement = 10f
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        groupId = intent?.getStringExtra("groupId")
        userId = intent?.getStringExtra("userId")
        userName = intent?.getStringExtra("userName")

        if (groupId == null || userId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location? = result.lastLocation
                loc?.let { location ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            repo.setLocationForGroup(
                                groupId = groupId!!,
                                uid = userId!!,
                                name = userName ?: "",
                                lat = location.latitude,
                                lng = location.longitude
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        fusedClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { fusedClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}

        if (groupId != null && userId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try { repo.stopSharingForGroup(groupId!!, userId!!) } catch (_: Exception) {}
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sharing location")
            .setContentText("Your live location is being shared with the group")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Location sharing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notifications for live location sharing"
            }
            nm.createNotificationChannel(ch)
        }
    }
}
