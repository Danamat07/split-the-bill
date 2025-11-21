package com.example.whopaid.ui.groups

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.whopaid.R
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityLocationSettingsBinding
import com.example.whopaid.models.Group
import com.example.whopaid.repo.GroupRepository
import com.example.whopaid.service.ForegroundLocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationSettingsBinding
    private val authRepo = AuthRepository()
    private val groupRepo = GroupRepository()

    private var groupId: String? = null
    private var group: Group? = null
    private var isSharing = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (!fine && !coarse) {
            Toast.makeText(this, getString(R.string.location_permission_required), Toast.LENGTH_SHORT).show()
            binding.switchShare.isChecked = false
        } else if (isSharing) {
            startLocationService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            Toast.makeText(this, getString(R.string.group_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load group asynchronously
        CoroutineScope(Dispatchers.Main).launch {
            group = groupRepo.getGroupById(groupId!!)
            val groupName = group?.name ?: getString(R.string.group_unknown)
            binding.btnGroupId.text = getString(R.string.group_label, groupName)
        }

        // Check if service is running
        isSharing = ForegroundLocationService.isServiceRunning(this)
        binding.switchShare.isChecked = isSharing

        binding.switchShare.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            isSharing = checked
            if (checked) requestLocationPermissionsThenStart()
            else stopLocationService()
        }
    }

    private fun requestLocationPermissionsThenStart() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) startLocationService()
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun startLocationService() {
        val currentUser = authRepo.currentUser()
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.must_be_logged_in), Toast.LENGTH_SHORT).show()
            binding.switchShare.isChecked = false
            return
        }

        val intent = Intent(this, ForegroundLocationService::class.java).apply {
            putExtra("groupId", groupId)
            putExtra("userId", currentUser.uid)
            putExtra("userName", currentUser.displayName ?: currentUser.email ?: "Unknown")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)

        Toast.makeText(this, getString(R.string.started_sharing_location), Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationService() {
        val intent = Intent(this, ForegroundLocationService::class.java)
        stopService(intent)
        Toast.makeText(this, getString(R.string.stopped_sharing_location), Toast.LENGTH_SHORT).show()
    }
}
