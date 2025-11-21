package com.example.whopaid.ui.groups

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.example.whopaid.databinding.ActivityGroupMapBinding
import com.example.whopaid.repo.LocationRepository
import com.example.whopaid.repo.SharedLocation
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GroupMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupMapBinding
    private val locationRepo = LocationRepository()
    private var groupId: String? = null

    private val markers = mutableMapOf<String, Marker>()
    private var listener: ListenerRegistration? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null

    private val LOCATION_REQUEST_CODE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            Toast.makeText(this, "Group missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Configurare OSMdroid
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        Configuration.getInstance().load(applicationContext, prefs)
        Configuration.getInstance().osmdroidBasePath = File(cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(cacheDir, "osmdroid/tiles")

        if (hasLocationPermission()) {
            initMap()
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                initMap()
            } else {
                Toast.makeText(this, "Location permission is required to show map", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initMap() {
        val map = binding.mapview
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)

        // Overlay locație utilizator
        if (hasLocationPermission()) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
            myLocationOverlay?.enableMyLocation()
            myLocationOverlay?.runOnFirstFix {
                val loc = myLocationOverlay?.myLocation
                if (loc != null) {
                    // Asigurăm acces UI thread pentru setCenter
                    runOnUiThread {
                        map.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                    }
                }
            }
            map.overlays.add(myLocationOverlay)
        }

        binding.progressLoading.visibility = View.VISIBLE

        // Ascultăm locațiile din Firestore
        listener = locationRepo.observeGroupLocations(groupId!!) { list ->
            runOnUiThread {
                binding.progressLoading.visibility = View.GONE
                updateMarkers(list)
            }
        }
    }

    private fun updateMarkers(list: List<SharedLocation>) {
        val map = binding.mapview
        val presentUids = list.map { it.uid }.toSet()

        // Șterge marker-ele utilizatorilor care au oprit sharing-ul
        markers.keys.filter { it !in presentUids }.forEach { uid ->
            markers[uid]?.let { map.overlays.remove(it) }
            markers.remove(uid)
        }

        // Adaugă sau actualizează marker-ele
        for (loc in list) {
            if (!loc.isSharing) {
                markers[loc.uid]?.let { map.overlays.remove(it) }
                markers.remove(loc.uid)
                continue
            }

            val pos = GeoPoint(loc.lat, loc.lng)
            val marker = markers[loc.uid]
            if (marker == null) {
                val m = Marker(map).apply {
                    position = pos
                    title = loc.name.ifEmpty { "User" }
                    subDescription = loc.updatedAt?.let { tsToString(it) } ?: ""
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(m)
                markers[loc.uid] = m
            } else {
                marker.position = pos
                marker.subDescription = loc.updatedAt?.let { tsToString(it) } ?: ""
            }
        }

        // Centrează harta pe primul marker, dacă există
        if (markers.isNotEmpty()) {
            map.controller.setCenter(markers.values.first().position)
        }

        map.invalidate()
    }

    private fun tsToString(ts: Timestamp): String {
        val sdf = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())
        return sdf.format(ts.toDate())
    }

    override fun onResume() {
        super.onResume()
        binding.mapview.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        binding.mapview.onPause()
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        myLocationOverlay?.disableMyLocation()
    }
}
