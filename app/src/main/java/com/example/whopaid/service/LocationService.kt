package com.example.whopaid.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.example.whopaid.AuthRepository
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Service care actualizează locația curentă a utilizatorului în Firestore
 * și permite afișarea în timp real pe harta grupului.
 */
class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private val db = FirebaseFirestore.getInstance()
    private val authRepo = AuthRepository()
    private var currentUserUid: String? = null

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        currentUserUid = authRepo.currentUser()?.uid

        // Configurăm request-ul de locație cu update la fiecare 5 secunde sau la 1 metru
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Verificăm permisiunile de locație
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            val location: Location = result.lastLocation ?: return
            updateLocationInFirestore(location)
        }
    }

    /**
     * Actualizează documentul Firestore al utilizatorului
     * cu latitudine, longitudine și shareLocation = true
     */
    private fun updateLocationInFirestore(location: Location) {
        val uid = currentUserUid ?: return
        val data = mapOf(
            "locationLat" to location.latitude,
            "locationLng" to location.longitude,
            "shareLocation" to true
        )

        val userDocRef = db.collection("users").document(uid)
        userDocRef.update(data)
            .addOnFailureListener {
                // Dacă documentul nu există, îl creăm
                userDocRef.set(data)
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
