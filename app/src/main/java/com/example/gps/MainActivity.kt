package com.example.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.gps.databinding.ActivityMainBinding
import kotlin.math.round

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private var compassSensor: Sensor? = null
    private var currentAzimuth = 0f
    private val NOTIFICATION_CHANNEL_ID = "navigation_channel"
    private val NOTIFICATION_ID = 1001

    // Para almacenar puntos de referencia (simulando beacons)
    private val indoorBeacons = mutableListOf<IndoorBeacon>()
    private var currentBeacon: IndoorBeacon? = null
    private var savedLocation: Location? = null

    // Solicitud de permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Se requieren permisos para el correcto funcionamiento", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicialización de componentes
        setupSensors()
        setupLocation()
        createNotificationChannel()
        loadSavedData()
        setupBeacons()

        // Configurar botones
        binding.fabAddBeacon.setOnClickListener {
            addCurrentLocationAsBeacon()
        }

        binding.btnNavigate.setOnClickListener {
            startNavigation()
        }

        // Solicitar permisos
        requestPermissions()
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        compassSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
    }

    private fun setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocationUI(location)
                    checkNearbyBeacons(location)
                }
            }
        }
    }

    private fun updateLocationUI(location: Location) {
        binding.tvLatitude.text = "Latitud: ${location.latitude}"
        binding.tvLongitude.text = "Longitud: ${location.longitude}"
        savedLocation = location
        saveLocationData()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {

            val locationRequest = LocationRequest.create().apply {
                interval = 5000
                fastestInterval = 2000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        compassSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            currentAzimuth = event.values[0]
            binding.compassView.rotation = -currentAzimuth

            // Si estamos navegando hacia un beacon, actualizar dirección
            currentBeacon?.let { beacon ->
                savedLocation?.let { location ->
                    val bearing = location.bearingTo(beacon.toLocation())
                    val relativeBearing = (bearing - currentAzimuth + 360) % 360
                    binding.tvDirection.text = "Dirección: ${relativeBearing.toInt()}°"

                    // Actualizar flecha direccional
                    binding.arrowView.rotation = bearing - currentAzimuth
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere implementación específica
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Canal de Navegación"
            val descriptionText = "Notificaciones sobre navegación interior"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, content: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    private fun setupBeacons() {
        // Simulamos algunos beacons predefinidos (en una implementación real, estos podrían cargarse desde una base de datos)
        indoorBeacons.add(IndoorBeacon("Entrada", 19.504508, -99.146453, "Entrada principal"))
        indoorBeacons.add(IndoorBeacon("Cafetería", 19.504387, -99.146601, "Zona de alimentos"))
        indoorBeacons.add(IndoorBeacon("Biblioteca", 19.504254, -99.146209, "Centro de estudio"))

        // Cargar beacons guardados
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val savedBeaconsJson = preferences.getString("saved_beacons", null)
        // Aquí implementaríamos la deserialización JSON de los beacons guardados
    }

    private fun addCurrentLocationAsBeacon() {
        savedLocation?.let { location ->
            val beaconName = "Beacon ${indoorBeacons.size + 1}"
            val newBeacon = IndoorBeacon(
                beaconName,
                location.latitude,
                location.longitude,
                "Punto de referencia personalizado"
            )
            indoorBeacons.add(newBeacon)
            saveBeacons()

            vibrate(longArrayOf(0, 100, 50, 100))
            showNotification(
                "Nuevo punto agregado",
                "Se ha agregado un nuevo punto de referencia en tu ubicación actual"
            )

            Toast.makeText(this, "Nuevo beacon agregado: $beaconName", Toast.LENGTH_SHORT).show()
            updateBeaconsList()
        } ?: Toast.makeText(this, "Ubicación no disponible", Toast.LENGTH_SHORT).show()
    }

    private fun checkNearbyBeacons(location: Location) {
        var closestBeacon: IndoorBeacon? = null
        var minDistance = Float.MAX_VALUE

        for (beacon in indoorBeacons) {
            val beaconLocation = beacon.toLocation()
            val distance = location.distanceTo(beaconLocation)

            if (distance < minDistance) {
                minDistance = distance
                closestBeacon = beacon
            }
        }

        // Si estamos a menos de 10 metros de un beacon
        if (minDistance < 10 && closestBeacon != null && closestBeacon != currentBeacon) {
            currentBeacon = closestBeacon

            // Vibración distintiva
            vibrate(longArrayOf(0, 200, 100, 200, 100, 200))

            // Notificación
            showNotification(
                "Punto cercano detectado",
                "Estás cerca de: ${closestBeacon.name} - ${closestBeacon.description}"
            )

            binding.tvNearestBeacon.text = "Cercano: ${closestBeacon.name}"
        }
    }

    private fun startNavigation() {
        // Implementación simple: seleccionar el primer beacon como destino
        if (indoorBeacons.isNotEmpty()) {
            currentBeacon = indoorBeacons[0]
            binding.tvCurrentDestination.text = "Navegando hacia: ${currentBeacon?.name}"

            showNotification(
                "Navegación iniciada",
                "Navegando hacia: ${currentBeacon?.name}"
            )
        }
    }

    private fun saveLocationData() {
        savedLocation?.let { location ->
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)
            val editor = preferences.edit()
            editor.putString("last_latitude", location.latitude.toString())
            editor.putString("last_longitude", location.longitude.toString())
            editor.apply()
        }
    }

    private fun loadSavedData() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val lastLatString = preferences.getString("last_latitude", null)
        val lastLongString = preferences.getString("last_longitude", null)

        if (lastLatString != null && lastLongString != null) {
            try {
                val lastLat = lastLatString.toDouble()
                val lastLong = lastLongString.toDouble()

                val location = Location("saved").apply {
                    latitude = lastLat
                    longitude = lastLong
                }

                savedLocation = location
                binding.tvLastLocation.text = "Última ubicación: $lastLat, $lastLong"
            } catch (e: Exception) {
                // Error al convertir los valores guardados
            }
        }
    }

    private fun saveBeacons() {
        // Aquí implementaríamos la serialización JSON de los beacons
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = preferences.edit()
        // editor.putString("saved_beacons", beaconsJson)
        editor.apply()
    }

    private fun updateBeaconsList() {
        // Actualizar la UI con la lista de beacons
        val beaconsList = indoorBeacons.joinToString("\n") {
            "${it.name} (${it.description})"
        }
        binding.tvBeaconsList.text = beaconsList
    }

    // Clase para representar un beacon
    data class IndoorBeacon(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val description: String
    ) {
        fun toLocation(): Location {
            return Location("beacon").apply {
                this.latitude = this@IndoorBeacon.latitude
                this.longitude = this@IndoorBeacon.longitude
            }
        }
    }
}