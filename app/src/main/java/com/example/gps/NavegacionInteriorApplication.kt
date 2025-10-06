package com.example.gps

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NavegacionInteriorApplication : Application() {

    companion object {
        const val NAVIGATION_CHANNEL_ID = "navigation_channel"
        const val BEACON_CHANNEL_ID = "beacon_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Canal para navegación
            val navigationChannel = NotificationChannel(
                NAVIGATION_CHANNEL_ID,
                "Navegación",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de navegación interior"
            }

            // Canal para beacons
            val beaconChannel = NotificationChannel(
                BEACON_CHANNEL_ID,
                "Beacons",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Detección de beacons cercanos"
            }

            notificationManager.createNotificationChannel(navigationChannel)
            notificationManager.createNotificationChannel(beaconChannel)
        }
    }
}