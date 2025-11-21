package com.example.whopaid.ui.groups

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.R
import com.example.whopaid.models.User
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class GroupMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private var groupId: String? = null
    private var membersListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_map)

        groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize map fragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        loadLiveMemberLocations()
    }

    private fun loadLiveMemberLocations() {
        val gid = groupId ?: return

        // Listen to members collection inside the group
        membersListener = db.collection("users")
            .whereArrayContains("groups", gid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading members: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                mMap.clear() // Remove old markers

                var firstLocation: LatLng? = null

                for (doc in snapshots.documents) {
                    val user = doc.toObject(User::class.java) ?: continue
                    if (user.shareLocation && user.locationLat != null && user.locationLng != null) {
                        val position = LatLng(user.locationLat!!, user.locationLng!!)
                        mMap.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(user.name ?: "Unknown")
                        )
                        if (firstLocation == null) firstLocation = position
                    }
                }

                // Move camera to first user's location
                firstLocation?.let {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        membersListener?.remove() // Stop listening when activity is destroyed
    }
}
