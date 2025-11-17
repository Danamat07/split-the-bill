package com.example.whopaid.ui.groups

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.whopaid.AuthRepository
import com.example.whopaid.databinding.ActivityScanQrBinding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class ScanQrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanQrBinding
    private lateinit var barcodeView: DecoratedBarcodeView
    private val db = FirebaseFirestore.getInstance()
    private val authRepo = AuthRepository()

    // Permission launcher (Activity Result API)
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraScan() else {
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
        }
    }

    // Gallery picker
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) decodeImageUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        barcodeView = binding.barcodeScanner

        binding.btnFromGallery.setOnClickListener {
            openGalleryPicker()
        }

        binding.btnToggleCameraScan.setOnClickListener {
            // ask permission and start camera continuous scan
            checkCameraPermissionAndStart()
        }

        // start paused; user must press button to request camera (sa nu cerem permission imediat)
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraScan() {
        // use continuous decode callback to handle results
        barcodeView.decodeContinuous(object: com.journeyapps.barcodescanner.BarcodeCallback {
            override fun barcodeResult(result: com.journeyapps.barcodescanner.BarcodeResult?) {
                if (result == null) return
                barcodeView.pause() // pause to avoid duplicate callbacks
                handleScannedText(result.text)
            }
            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
        })
    }

    private fun openGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun decodeImageUri(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= 29) {
                val src = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(src)
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            val text = decodeBitmapWithZXing(bitmap)
            if (text != null) {
                handleScannedText(text)
            } else {
                Toast.makeText(this, "No QR code found in image", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to decode image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun decodeBitmapWithZXing(bitmap: Bitmap): String? {
        val intArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        return try {
            val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE))
            val result = reader.decode(binaryBitmap, hints)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    private fun handleScannedText(text: String) {
        // Expect format: JOIN_GROUP:{groupId}
        if (!text.startsWith("JOIN_GROUP:")) {
            Toast.makeText(this, "Scanned QR is not a join code", Toast.LENGTH_LONG).show()
            barcodeView.resume()
            return
        }
        val groupId = text.removePrefix("JOIN_GROUP:")
        val current = authRepo.currentUser()
        if (current == null) {
            Toast.makeText(this, "You must be logged in to join a group", Toast.LENGTH_LONG).show()
            return
        }

        // Add current uid to group's members array (Firestore)
        db.collection("groups").document(groupId)
            .update("members", FieldValue.arrayUnion(current.uid))
            .addOnSuccessListener {
                Toast.makeText(this, "Joined group successfully", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to join group: ${e.message}", Toast.LENGTH_LONG).show()
                barcodeView.resume()
            }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}
