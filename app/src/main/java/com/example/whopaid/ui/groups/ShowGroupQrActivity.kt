package com.example.whopaid.ui.groups

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.whopaid.databinding.ActivityShowGroupQrBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.*

class ShowGroupQrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShowGroupQrBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShowGroupQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val groupId = intent.getStringExtra("groupId")
        if (groupId == null) {
            finish()
            return
        }

        val payload = "JOIN_GROUP:$groupId" // formatul QR-ului
        val bitmap = generateQrBitmap(payload, 800, 800)
        if (bitmap != null) {
            binding.imageQr.setImageBitmap(bitmap)
            binding.progressBar.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.textError.visibility = View.VISIBLE
            binding.textError.text = "Eroare generare QR"
        }

        binding.btnShare.setOnClickListener {
            // share QR as image via intent - simplu: share text link (payload). Dacă vrei sharing imagine complex, salvare bitmap local etc.
            val share = Intent()
            share.action = Intent.ACTION_SEND
            share.putExtra(Intent.EXTRA_TEXT, payload)
            share.type = "text/plain"
            startActivity(Intent.createChooser(share, "Share QR payload"))
        }
    }

    private fun generateQrBitmap(text: String, width: Int, height: Int): Bitmap? {
        return try {
            val hints = Hashtable<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
