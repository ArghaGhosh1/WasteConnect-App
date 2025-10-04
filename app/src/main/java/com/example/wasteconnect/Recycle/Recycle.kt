package com.example.wasteconnect.Recycle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.wasteconnect.MainActivity
import com.example.wasteconnect.R
import com.example.wasteconnect.RecycleReport
import com.example.wasteconnect.ml.Model2
import com.example.wasteconnect.ui.supabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Recycle : AppCompatActivity() {

    lateinit var photoFile: File
    val REQUEST_IMAGE_CAPTURE = 1
    lateinit var resizedBitmap: Bitmap


    private lateinit var clickPic: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLocation: TextView
    private lateinit var backButton: ImageView
    private lateinit var submitButton: CardView
    private lateinit var des: TextInputEditText
    private lateinit var loc: TextInputEditText
    lateinit var selectItem1: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recycle)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        clickPic = findViewById(R.id.btnOpenCamera)
        submitButton = findViewById(R.id.submit_button_card)
        des = findViewById(R.id.description_input)
        loc = findViewById(R.id.location_input)

        //camera access
        clickPic.setOnClickListener {
            photoFile = File(getExternalFilesDir(null), "profile_pic.jpg")
            val photoURI =
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)

            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }

        ///location access
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tvLocation = findViewById(R.id.location_input)

        val btnGetLocation = findViewById<CardView>(R.id.get_location_button)
        btnGetLocation.setOnClickListener {
            getLocation()
        }

        //back to main activity
        backButton = findViewById(R.id.back_button)

        backButton.setOnClickListener {
            Intent(this, MainActivity::class.java).also {
                startActivity(it)
            }
        }

        submitButton.setOnClickListener {

            if (loc.text!!.isEmpty() || des.text!!.isEmpty()) {

                Toast.makeText(this, "Please fill all the fields", Toast.LENGTH_SHORT).show()
            } else {
                CoroutineScope(Dispatchers.IO).launch {

                    try {
                        val city = RecycleReport(
                            selectItem1,
                            des.text.toString(),
                            loc.text.toString(),

                            )
                        supabase.from("Recycle").insert(city) {
                            select()
                        }.decodeSingle<RecycleReport>()

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@Recycle,
                                "Your request is submitted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@Recycle, "error occured", Toast.LENGTH_SHORT).show()
                        }
                    }

                    try {
                        val imagePath = "photos/${System.currentTimeMillis()}.jpg"

                        supabase.storage.from("Recycle").upload(
                            path = imagePath, data = photoFile.readBytes()
                        )
                    } catch (e: Exception) {

                    }

                    Intent(this@Recycle, MainActivity::class.java).also {
                        startActivity(it)
                        finish()
                    }

                }
            }
        }


        //ENDDDDDDDDDDDDDD
    }

    private fun runMLModel(bitmap: Bitmap) {
        val model = Model2.newInstance(this)

        val byteBuffer = convertBitmapToByteBuffer(bitmap)

        val inputFeature0 = TensorBuffer.createFixedSize(
            intArrayOf(1, 224, 224, 3),
            DataType.FLOAT32
        )
        inputFeature0.loadBuffer(byteBuffer)

        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        val probabilities = outputFeature0.floatArray

        val labels = assets.open("labels2.txt").bufferedReader().readLines()

        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        val label = labels[maxIndex]
        val confidence = probabilities[maxIndex] * 100

        val mlView = findViewById<TextView>(R.id.tv_isRecycle)

        mlView.text = "$label"

        if (mlView.text == "Not Recyclable") {
            submitButton.isEnabled = false
            submitButton.alpha = 0.5f
        } else {
            submitButton.isEnabled = true
            submitButton.alpha = 1f
        }



        model.close()
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(224 * 224)
        bitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        var pixelIndex = 0
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixelValue = intValues[pixelIndex++]

                val r = (pixelValue shr 16 and 0xFF).toByte()
                val g = (pixelValue shr 8 and 0xFF).toByte()
                val b = (pixelValue and 0xFF).toByte()

                byteBuffer.put(r)
                byteBuffer.put(g)
                byteBuffer.put(b)
            }
        }
        return byteBuffer
    }

    //camera access
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            findViewById<ImageView>(R.id.imageView).setImageBitmap(bitmap)

            // ✅ Initialize resizedBitmap here
            resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

            // ✅ Run ML model after photo is captured
            runMLModel(resizedBitmap)
        }
    }

    //spinner


    //location access
    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101
            )
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    tvLocation.text = "$latitude , $longitude"
                } else {
                    tvLocation.text = "Unable to get location"
                }
            }
    }
}