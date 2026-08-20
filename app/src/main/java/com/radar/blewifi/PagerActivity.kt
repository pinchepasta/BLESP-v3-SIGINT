package com.radar.blewifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.radar.blewifi.databinding.ActivityPagerBinding
import java.util.*

class PagerActivity : AppCompatActivity(), ScannerManager.ScanListener {

    private lateinit var binding: ActivityPagerBinding
    private lateinit var scanner: ScannerManager
    private lateinit var nearbyManager: NearbyManager
    private val deviceList = mutableListOf<ScanDevice>()
    private lateinit var adapter: PagerDeviceAdapter
    private lateinit var chatAdapter: ChatHistoryAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    data class ChatMessage(
        val id: String, 
        val sender: String, 
        val text: String, 
        val timestamp: Long, 
        val isMe: Boolean,
        val audioPayloadId: Long? = null,
        var audioPath: String? = null,
        var filePath: String? = null,
        var fileName: String? = null
    )
    private val allMessages = java.util.concurrent.ConcurrentHashMap<String, MutableList<ChatMessage>>() // Key: Callsign
    private val unreadCount = java.util.concurrent.ConcurrentHashMap<String, Int>() // Key: Callsign
    private val handler = Handler(Looper.getMainLooper())
    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    
    companion object {
        private var sessionHistoryCleared = false
        var activeChatId: String? = null
    }

    private val refreshChatRunnable = object : Runnable {
        override fun run() {
            if (activeChatId != null) {
                updateChatUI()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun updateChatUI() {
        activeChatId?.let { chatId ->
            val messages = allMessages[chatId] ?: mutableListOf()
            chatAdapter.updateData(messages)
            if (messages.isNotEmpty()) {
                binding.rvChatHistory.post {
                    binding.rvChatHistory.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private var isHighContrastMode: Boolean = false
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var audioFile: java.io.File? = null

    private fun playAudio(message: ChatMessage) {
        try {
            val path = message.audioPath ?: return
            val file = java.io.File(path)
            if (!file.exists()) {
                android.widget.Toast.makeText(this, "Audio file not found", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            // Stop and release previous player
            mediaPlayer?.stop()
            mediaPlayer?.release()
            
            mediaPlayer = android.media.MediaPlayer().apply {
                val fis = java.io.FileInputStream(file)
                setDataSource(fis.fd)
                fis.close()
                prepare()
                start()
                setOnCompletionListener { 
                    it.release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(this, "Playback error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Reset unread status when entering Pager
        getSharedPreferences("pager_history", MODE_PRIVATE).edit().putBoolean("has_unread", false).apply()

        val themeName = getSharedPreferences("settings", MODE_PRIVATE).getString("theme_name", "DEFAULT")
        val theme = RadarView.Theme.valueOf(themeName ?: "DEFAULT")
        isHighContrastMode = theme == RadarView.Theme.HIGH_CONTRAST

        setupImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as RadarApplication
        scanner = app.scanner
        nearbyManager = app.nearbyManager

        nearbyManager.addListener(nearbyListener)
        setupUI()
        
        nearbyManager.start()
    }

    private val nearbyListener = object : NearbyManager.NearbyListener {
        override fun onMessageReceived(senderCallsign: String, text: String) {
            val msg = ChatMessage(UUID.randomUUID().toString(), senderCallsign, text, System.currentTimeMillis(), false)
            allMessages.getOrPut(senderCallsign) { mutableListOf() }.add(msg)
            saveMessages()
            
            if (activeChatId == senderCallsign) {
                runOnUiThread { updateChatUI() }
            } else {
                unreadCount[senderCallsign] = (unreadCount[senderCallsign] ?: 0) + 1
            }
            runOnUiThread { onDevicesUpdated(scanner.getDevices()) }
        }

        override fun onVoiceMessageReceived(senderCallsign: String, filePath: String) {
            val msg = ChatMessage(UUID.randomUUID().toString(), senderCallsign, "[VOICE_MAIL]", System.currentTimeMillis(), false, audioPath = filePath)
            allMessages.getOrPut(senderCallsign) { mutableListOf() }.add(msg)
            saveMessages()

            if (activeChatId == senderCallsign) {
                runOnUiThread { updateChatUI() }
            } else {
                unreadCount[senderCallsign] = (unreadCount[senderCallsign] ?: 0) + 1
            }
            runOnUiThread { onDevicesUpdated(scanner.getDevices()) }
        }

        override fun onFileReceived(senderCallsign: String, filePath: String, fileName: String) {
            val mediaDir = java.io.File(filesDir, "media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            val sourceFile = java.io.File(filePath)
            val destFile = java.io.File(mediaDir, fileName)

            try {
                sourceFile.copyTo(destFile, overwrite = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val finalPath = destFile.absolutePath
            val isVoicemail = fileName.startsWith("voicemail_") || fileName.endsWith(".m4a") || fileName.endsWith(".amr")

            val msg = if (isVoicemail) {
                ChatMessage(UUID.randomUUID().toString(), senderCallsign, "[VOICE_MAIL]", System.currentTimeMillis(), false, audioPath = finalPath)
            } else {
                ChatMessage(UUID.randomUUID().toString(), senderCallsign, "[FILE: $fileName]", System.currentTimeMillis(), false, filePath = finalPath, fileName = fileName)
            }
            allMessages.getOrPut(senderCallsign) { mutableListOf() }.add(msg)
            saveMessages()

            if (activeChatId == senderCallsign) {
                runOnUiThread { updateChatUI() }
            } else {
                unreadCount[senderCallsign] = (unreadCount[senderCallsign] ?: 0) + 1
            }
            runOnUiThread { onDevicesUpdated(scanner.getDevices()) }
        }

        override fun onDeviceFound(callsign: String) {
            runOnUiThread { onDevicesUpdated(scanner.getDevices()) }
        }

        override fun onDeviceLost(callsign: String) {
            runOnUiThread { onDevicesUpdated(scanner.getDevices()) }
        }
    }

    private fun loadMessages() {
        ioExecutor.execute {
            try {
                val prefs = getSharedPreferences("pager_history", MODE_PRIVATE)
                val json = prefs.getString("all_messages", "{}")
                val type = object : TypeToken<MutableMap<String, MutableList<ChatMessage>>>() {}.type
                val loaded: MutableMap<String, MutableList<ChatMessage>> = Gson().fromJson(json, type) ?: mutableMapOf()
                
                runOnUiThread {
                    allMessages.clear()
                    allMessages.putAll(loaded)
                    onDevicesUpdated(scanner.getDevices())
                    if (activeChatId != null) {
                        updateChatUI()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveMessages() {
        val messagesSnapshot = HashMap(allMessages)
        ioExecutor.execute {
            try {
                val prefs = getSharedPreferences("pager_history", MODE_PRIVATE)
                val json = Gson().toJson(messagesSnapshot)
                prefs.edit().putString("all_messages", json).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun confirmDeleteChat(chatName: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("DELETE_DATA")
            .setMessage("CONFIRM_ERASURE_OF_CHAT: $chatName?")
            .setPositiveButton("DELETE") { _, _ -> deleteChat(chatName) }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun deleteChat(chatName: String) {
        allMessages.remove(chatName)
        saveMessages()
        if (activeChatId == chatName) {
            closeChat()
        }
        onDevicesUpdated(scanner.getDevices())
    }

    private fun setupUI() {
        val themeName = getSharedPreferences("settings", MODE_PRIVATE).getString("theme_name", "DEFAULT")
        val currentTheme = RadarView.Theme.valueOf(themeName ?: "DEFAULT")
        isHighContrastMode = currentTheme == RadarView.Theme.HIGH_CONTRAST

        val primaryColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
            RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
            RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
            else -> Color.parseColor("#00FF41")
        }

        val dimColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.GRAY
            RadarView.Theme.RED_NIGHT -> Color.parseColor("#660000")
            RadarView.Theme.PINK -> Color.parseColor("#990099")
            RadarView.Theme.NEON -> Color.parseColor("#B3C403")
            RadarView.Theme.NARANJA -> Color.parseColor("#995400")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#660066")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#663f2b")
            else -> Color.GRAY
        }

        binding.root.setBackgroundColor(if (isHighContrastMode) Color.WHITE else Color.BLACK)
        
        binding.tvIdLabel.setTextColor(primaryColor)
        binding.etUserId.setTextColor(primaryColor)
        binding.etUserId.setHintTextColor(dimColor)
        binding.tvStatus.setTextColor(primaryColor)
        binding.separator.setBackgroundColor(primaryColor)
        binding.tvDiscoveryLabel.setTextColor(primaryColor)
        binding.tvChatWith.setTextColor(primaryColor)

        binding.btnBack.setBackgroundResource(when(currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.status_box_bg_white
            RadarView.Theme.RED_NIGHT -> R.drawable.status_box_bg_red
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.status_box_bg_neon
            RadarView.Theme.NARANJA -> R.drawable.status_box_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.status_box_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.status_box_bg_naranja
            else -> R.drawable.status_box_bg
        })
        binding.btnBack.setTextColor(if (currentTheme == RadarView.Theme.BUBBLEGUM) Color.parseColor("#00FDFF") else if (isHighContrastMode) Color.BLACK else Color.WHITE)

        binding.etMessage.setBackgroundResource(when(currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.status_box_bg_white
            RadarView.Theme.RED_NIGHT -> R.drawable.status_box_bg_red
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.status_box_bg_neon
            RadarView.Theme.NARANJA -> R.drawable.status_box_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.status_box_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.status_box_bg_naranja
            else -> R.drawable.status_box_bg
        })
        binding.etMessage.setTextColor(if (isHighContrastMode) Color.BLACK else Color.WHITE)
        binding.etMessage.setHintTextColor(dimColor)

        binding.btnSend.setBackgroundResource(when(currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.status_box_bg_white
            RadarView.Theme.RED_NIGHT -> R.drawable.status_box_bg_red
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.status_box_bg_neon
            RadarView.Theme.NARANJA -> R.drawable.status_box_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.status_box_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.status_box_bg_naranja
            else -> R.drawable.status_box_bg
        })
        binding.btnSend.setTextColor(if (currentTheme == RadarView.Theme.BUBBLEGUM) Color.parseColor("#00FDFF") else primaryColor)

        binding.btnAttach.setTextColor(primaryColor)
        binding.btnRecord.setTextColor(primaryColor)

        adapter = PagerDeviceAdapter(deviceList, currentTheme, { device ->
            openChat(device)
        }, { device ->
            confirmDeleteChat(device.displayName)
        }, { device ->
            confirmDeleteChat(device.displayName)
        })
        
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = adapter

        chatAdapter = ChatHistoryAdapter(chatMessages, currentTheme, { msg ->
            val isAudio = msg.audioPath != null || msg.audioPayloadId != null
            if (isAudio) {
                playAudio(msg)
            } else if (msg.filePath != null) {
                openFile(msg)
            }
        })
        binding.rvChatHistory.layoutManager = LinearLayoutManager(this)
        binding.rvChatHistory.adapter = chatAdapter

        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.15) { // keyboard is opened
                if (chatMessages.isNotEmpty()) {
                    binding.rvChatHistory.post {
                        binding.rvChatHistory.scrollToPosition(chatMessages.size - 1)
                    }
                }
            }
        }

        val prefs = getSharedPreferences("pager_prefs", MODE_PRIVATE)
        val savedUserId = prefs.getString("user_id", null)
        if (savedUserId == null) {
            val newId = "UNIT_${(100..999).random()}"
            prefs.edit().putString("user_id", newId).apply()
            binding.etUserId.setText(newId)
        } else {
            binding.etUserId.setText(savedUserId)
        }

        binding.etUserId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newId = s.toString().trim()
                if (newId.isNotEmpty()) {
                    prefs.edit().putString("user_id", newId).apply()
                    nearbyManager.updateMyCallsign(newId)
                }
            }
        })

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                sendMessage()
                true
            } else {
                false
            }
        }

        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.btnAttach.setOnClickListener {
            openFilePicker()
        }

        binding.btnRecord.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if (checkAudioPermission()) {
                        startRecording()
                        v.setBackgroundResource(R.drawable.status_box_bg_green)
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    v.setBackgroundResource(R.drawable.status_box_bg)
                }
            }
            true
        }

        binding.btnBack.setOnClickListener {
            if (binding.layoutChat.visibility == View.VISIBLE) {
                closeChat()
            } else {
                finish()
            }
        }
        
        loadMessages()
        onDevicesUpdated(scanner.getDevices())
    }

    private fun checkAudioPermission(): Boolean {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
            return false
        }
        return true
    }

    private fun startRecording() {
        try {
            audioFile = java.io.File(cacheDir, "voicemail_${System.currentTimeMillis()}.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.media.MediaRecorder(this)
            } else {
                android.media.MediaRecorder()
            }.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    sendVoiceMessage(file)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendVoiceMessage(file: java.io.File) {
        activeChatId?.let { chatId ->
            val myName = binding.etUserId.text.toString()
            val msg = ChatMessage(UUID.randomUUID().toString(), myName, "[VOICE_MAIL]", System.currentTimeMillis(), true, audioPath = file.absolutePath)
            allMessages.getOrPut(chatId) { mutableListOf() }.add(msg)
            saveMessages()
            
            nearbyManager.sendVoiceMessage(chatId, file)
            updateChatUI()
        }
    }


    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select File"), 1002)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                handleSelectedFile(uri)
            }
        }
    }

    private fun handleSelectedFile(uri: android.net.Uri) {
        val fileName = getFileName(uri)
        val file = copyUriToFile(uri, fileName)
        if (file != null) {
            sendFile(file)
        }
    }

    private fun getFileName(uri: android.net.Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun copyUriToFile(uri: android.net.Uri, fileName: String): java.io.File? {
        return try {
            val file = java.io.File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sendFile(file: java.io.File) {
        activeChatId?.let { chatId ->
            val myName = binding.etUserId.text.toString()
            val msg = ChatMessage(UUID.randomUUID().toString(), myName, "[FILE: ${file.name}]", System.currentTimeMillis(), true, filePath = file.absolutePath, fileName = file.name)
            allMessages.getOrPut(chatId) { mutableListOf() }.add(msg)
            saveMessages()
            
            nearbyManager.sendFile(chatId, file)
            updateChatUI()
        }
    }

    private fun openFile(message: ChatMessage) {
        val path = message.filePath ?: return
        val file = java.io.File(path)
        if (!file.exists()) return

        val isImage = message.fileName?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") } ?: false
        if (isImage) {
            showEnlargedImage(file)
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open File"))
    }

    private fun showEnlargedImage(file: java.io.File) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        
        val imageView = android.widget.ImageView(this)
        imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        imageView.adjustViewBounds = true
        imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        
        try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                return
            }
        } catch (e: Exception) {
            return
        }
        
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return
        
        activeChatId?.let { chatId ->
            val myName = binding.etUserId.text.toString()
            val msg = ChatMessage(UUID.randomUUID().toString(), myName, text, System.currentTimeMillis(), true)
            allMessages.getOrPut(chatId) { mutableListOf() }.add(msg)
            saveMessages()
            
            // Send via Nearby (WiFi/P2P)
            nearbyManager.sendMessage(chatId, text)
            (application as? RadarApplication)?.recordSentMessage(text)
            
            binding.etMessage.setText("")
            updateChatUI()
        }
    }

    private fun openChat(device: ScanDevice) {
        activeChatId = device.displayName
        unreadCount[device.displayName] = 0
        
        // Save as last interacted contact to keep it at the top
        getSharedPreferences("pager_prefs", MODE_PRIVATE).edit()
            .putLong("last_interaction_${device.displayName}", System.currentTimeMillis())
            .apply()

        binding.tvChatWith.text = "CHAT_WITH: ${device.displayName}"
        binding.layoutChat.visibility = View.VISIBLE
        binding.layoutMessageInput.visibility = View.VISIBLE
        binding.rvDevices.visibility = View.GONE
        binding.tvDiscoveryLabel.visibility = View.GONE
        binding.tvStatus.text = "[ ACTIVE_CHAT ]"
        updateChatUI()
    }

    private fun closeChat() {
        activeChatId = null
        binding.layoutChat.visibility = View.GONE
        binding.rvDevices.visibility = View.VISIBLE
        binding.tvDiscoveryLabel.visibility = View.VISIBLE
        binding.layoutMessageInput.visibility = View.GONE
        updateStatus()
    }


    private fun updateStatus() {
        val themeName = getSharedPreferences("settings", MODE_PRIVATE).getString("theme_name", "DEFAULT")
        val currentTheme = RadarView.Theme.valueOf(themeName ?: "DEFAULT")
        
        val primaryColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
            RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
            RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
            else -> Color.parseColor("#00FF41")
        }

        val discoveryColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
            RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
            RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#6befff")
            else -> Color.parseColor("#FF00FF") // Default discovery color was pinkish
        }

        if (activeChatId != null) {
            binding.tvStatus.text = "[ ACTIVE_CHAT ]"
            binding.tvStatus.setTextColor(primaryColor)
        } else {
            binding.tvStatus.text = "[ DISCOVERY ]"
            binding.tvStatus.setTextColor(discoveryColor)
        }
    }

    override fun onDevicesUpdated(devices: List<ScanDevice>) {
        val myName = binding.etUserId.text.toString()
        
        // Use the map directly for O(1) lookups instead of filtering a list repeatedly
        val chatList = mutableMapOf<String, ScanDevice>()

        // Process only PAGER types efficiently
        scanner.deviceMap.forEach { (_, device) ->
            if (device.type == DeviceType.PAGER && device.displayName != myName) {
                chatList[device.displayName] = device
            }
        }

        // Merge with history and connectivity status
        allMessages.keys.forEach { name ->
            if (name != myName) {
                val device = chatList[name] ?: ScanDevice(name, name, DeviceType.PAGER, -127, name)
                
                val history = allMessages[name]
                val lastMsg = history?.lastOrNull()
                device.lastMessage = lastMsg?.text
                device.lastMessageTime = lastMsg?.timestamp ?: 0
                device.seenCount = unreadCount[name] ?: 0
                
                if (nearbyManager.isConnected(name)) {
                    device.rssi = -50 
                }
                
                chatList[name] = device
            }
        }

        val archivedPagers = scanner.getArchivedDevices()
            .filter { it.type == DeviceType.PAGER }
            .associateBy { it.displayName }

        val combinedList = (chatList.values + archivedPagers.values)
            .distinctBy { it.displayName }
            .sortedByDescending { 
                maxOf(it.lastSeen, it.lastMessageTime, getSharedPreferences("pager_prefs", MODE_PRIVATE).getLong("last_interaction_${it.displayName}", 0L))
            }
            .take(3) // Only show the last 3 chats
        
        adapter.updateData(combinedList)
    }

    private fun flashScreenGreen() {
        triggerGlitchEffect()
    }

    private fun triggerGlitchEffect() {
        val themeName = getSharedPreferences("settings", MODE_PRIVATE).getString("theme_name", "DEFAULT")
        val currentTheme = RadarView.Theme.valueOf(themeName ?: "DEFAULT")
        
        val primaryColorStr = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> "#000000"
            RadarView.Theme.RED_NIGHT -> "#FF0000"
            RadarView.Theme.PINK -> "#FF00FF"
            RadarView.Theme.NEON -> "#E6FB04"
            RadarView.Theme.NARANJA -> "#FF8C00"
            RadarView.Theme.BUBBLEGUM -> "#FF00FF"
            else -> "#00FF41"
        }

        val overlay = View(this)
        overlay.setBackgroundColor(Color.parseColor(primaryColorStr))
        overlay.alpha = 0.4f
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        (window.decorView as ViewGroup).addView(overlay, params)

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var frames = 0
        val glitchAction = object : Runnable {
            override fun run() {
                if (frames > 2) {
                    (window.decorView as ViewGroup).removeView(overlay)
                    window.decorView.translationX = 0f
                    window.decorView.translationY = 0f
                    return
                }

                val isPrimary = frames % 2 == 0
                overlay.setBackgroundColor(if (isPrimary) Color.parseColor(primaryColorStr) else Color.WHITE)
                overlay.alpha = 0.5f
                
                window.decorView.translationX = ((-15..15).random()).toFloat()
                window.decorView.translationY = ((-10..10).random()).toFloat()

                frames++
                handler.postDelayed(this, 100)
            }
        }
        handler.post(glitchAction)
        
        // Vibration
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    override fun onScanStatusChanged(scanning: Boolean) {}
    override fun onMovementDetected(device: ScanDevice) {}
    override fun onLocationUpdated(lat: Double, lon: Double) {}
    override fun onError(error: String) {}

    override fun onStart() {
        super.onStart()
        loadMessages()
        scanner.addListener(this)
        scanner.startScanning() // Auto-start scanning when Pager is opened
        nearbyManager.start()
        handler.removeCallbacks(refreshChatRunnable)
        handler.post(refreshChatRunnable)
        setupImmersiveMode() // Re-apply in case it was lost
    }

    override fun onStop() {
        super.onStop()
        scanner.removeListener(this)
        handler.removeCallbacks(refreshChatRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager.removeListener(nearbyListener)
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        if (binding.layoutChat.visibility == View.VISIBLE) {
            closeChat()
        } else {
            finish()
        }
    }

    inner class ChatHistoryAdapter(
        private var messages: List<ChatMessage>,
        private val currentTheme: RadarView.Theme,
        private val onItemClick: (ChatMessage) -> Unit
    ) : RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder>() {

        fun updateData(newMessages: List<ChatMessage>) {
            val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize(): Int = messages.size
                override fun getNewListSize(): Int = newMessages.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean = 
                    messages[oldPos].id == newMessages[newPos].id
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                    messages[oldPos] == newMessages[newPos]
            }
            val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
            messages = newMessages.toList()
            diffResult.dispatchUpdatesTo(this)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val container: View = view.findViewById(R.id.messageContainer)
            val text: TextView = view.findViewById(R.id.tvMessage)
            val info: TextView = view.findViewById(R.id.tvTime)
            val audioIcon: TextView = view.findViewById(R.id.tvAudioIcon)
            val image: android.widget.ImageView = view.findViewById(R.id.ivImage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = messages[position]
            
            val isAudio = msg.audioPath != null || msg.audioPayloadId != null
            val isImage = msg.fileName?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") } ?: false

            if (isAudio) {
                holder.text.visibility = View.GONE
                holder.image.visibility = View.GONE
                holder.audioIcon.visibility = View.VISIBLE
            } else if (isImage && msg.filePath != null) {
                holder.text.visibility = View.GONE
                holder.audioIcon.visibility = View.GONE
                holder.image.visibility = View.VISIBLE
                try {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = 2 // Load smaller version for thumbnail
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeFile(msg.filePath, options)
                    if (bitmap != null) {
                        holder.image.setImageBitmap(bitmap)
                    } else {
                        holder.image.setImageResource(android.R.drawable.ic_menu_report_image)
                    }
                } catch (e: Exception) {
                    holder.image.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                holder.text.visibility = View.VISIBLE
                holder.audioIcon.visibility = View.GONE
                holder.image.visibility = View.GONE
                holder.text.text = msg.text
            }
            
            holder.container.setOnClickListener { onItemClick(msg) }
            holder.image.setOnClickListener { onItemClick(msg) }
            
            val timeStr = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            holder.info.text = timeStr

            val params = holder.container.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            
            if (msg.isMe) {
                params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                params.horizontalBias = 1.0f
                holder.container.setBackgroundResource(R.drawable.chat_bubble_sent)
                holder.text.setTextColor(Color.WHITE)
                holder.audioIcon.setTextColor(Color.WHITE)
                holder.info.setTextColor(Color.parseColor("#CCFFFFFF"))
            } else {
                params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                params.horizontalBias = 0.0f
                holder.container.setBackgroundResource(R.drawable.chat_bubble_received)
                
                val receivedColor = when(currentTheme) {
                    RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
                    RadarView.Theme.RED_NIGHT -> Color.RED
                    RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
                    RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
                    RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
                    RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
                    RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
                    else -> Color.parseColor("#00FF41")
                }
                
                holder.text.setTextColor(receivedColor)
                holder.audioIcon.setTextColor(receivedColor)
                
                val infoColor = when(currentTheme) {
                    RadarView.Theme.HIGH_CONTRAST -> Color.GRAY
                    RadarView.Theme.RED_NIGHT -> Color.parseColor("#66FF0000")
                    RadarView.Theme.PINK -> Color.parseColor("#66FF00FF")
                    RadarView.Theme.NEON -> Color.parseColor("#66E6FB04")
                    RadarView.Theme.NARANJA -> Color.parseColor("#66FF8C00")
                    RadarView.Theme.BUBBLEGUM -> Color.parseColor("#66FF00FF")
                    RadarView.Theme.SUMMERTIME -> Color.parseColor("#66ff9f6b")
                    else -> Color.parseColor("#6600FF41")
                }
                holder.info.setTextColor(infoColor)
            }
            holder.container.layoutParams = params
            
            holder.text.typeface = android.graphics.Typeface.MONOSPACE
            holder.audioIcon.typeface = android.graphics.Typeface.MONOSPACE
            holder.info.typeface = android.graphics.Typeface.MONOSPACE
        }

        override fun getItemCount() = messages.size
    }

    inner class PagerDeviceAdapter(
        private var devices: List<ScanDevice>,
        private val currentTheme: RadarView.Theme,
        private val onClick: (ScanDevice) -> Unit,
        private val onLongClick: (ScanDevice) -> Unit,
        private val onDeleteClick: (ScanDevice) -> Unit
    ) : RecyclerView.Adapter<PagerDeviceAdapter.ViewHolder>() {

        fun updateData(newDevices: List<ScanDevice>) {
            val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize(): Int = devices.size
                override fun getNewListSize(): Int = newDevices.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean = 
                    devices[oldPos].displayName == newDevices[newPos].displayName
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val old = devices[oldPos]
                    val new = newDevices[newPos]
                    return old.lastMessage == new.lastMessage && 
                           old.lastMessageTime == new.lastMessageTime &&
                           old.rssi == new.rssi &&
                           old.seenCount == new.seenCount
                }
            }
            val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
            devices = newDevices.toList()
            diffResult.dispatchUpdatesTo(this)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvDeviceName)
            val lastMessage: TextView = view.findViewById(R.id.tvLastMessage)
            val time: TextView = view.findViewById(R.id.tvTime)
            val avatarBg: View = view.findViewById(R.id.viewAvatarBg)
            val avatarText: TextView = view.findViewById(R.id.tvAvatarText)
            val statusIndicator: android.widget.ImageView = view.findViewById(R.id.viewStatusIndicator)
            val btnDelete: View = view.findViewById(R.id.btnDeleteChat)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pager_device, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            val isOnline = device.rssi > -127
            
            holder.name.text = device.displayName
            
            val themeColor = when(currentTheme) {
                RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
                RadarView.Theme.RED_NIGHT -> Color.RED
                RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
                RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
                RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
                RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
                RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
                else -> Color.parseColor("#00FF41")
            }
            holder.name.setTextColor(themeColor)
            
            // Thumbnail check
            val history = allMessages[device.displayName]
            val lastMsg = history?.lastOrNull()
            val isImage = lastMsg?.fileName?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") } ?: false
            
            if (isImage && lastMsg?.filePath != null) {
                holder.lastMessage.text = "📷 [PHOTO]"
            } else {
                holder.lastMessage.text = device.lastMessage ?: "No messages"
            }
            
            val lastMsgColor = when(currentTheme) {
                RadarView.Theme.HIGH_CONTRAST -> Color.DKGRAY
                RadarView.Theme.RED_NIGHT -> Color.parseColor("#88FF0000")
                RadarView.Theme.PINK -> Color.parseColor("#88FF00FF")
                RadarView.Theme.NEON -> Color.parseColor("#88E6FB04")
                RadarView.Theme.NARANJA -> Color.parseColor("#88FF8C00")
                RadarView.Theme.BUBBLEGUM -> Color.parseColor("#88FF00FF")
                RadarView.Theme.SUMMERTIME -> Color.parseColor("#88ff9f6b")
                else -> Color.parseColor("#8800FF41")
            }
            holder.lastMessage.setTextColor(lastMsgColor)
            
            if (device.lastMessageTime > 0) {
                val sdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                holder.time.text = sdf.format(Date(device.lastMessageTime))
                holder.time.visibility = View.VISIBLE
            } else {
                holder.time.visibility = View.GONE
            }

            // Avatar setup
            val firstChar = device.displayName.firstOrNull()?.toString() ?: "P"
            holder.avatarText.text = firstChar
            
            when(currentTheme) {
                RadarView.Theme.HIGH_CONTRAST -> {
                    holder.avatarBg.setBackgroundResource(R.drawable.status_box_bg_white)
                    holder.avatarText.setTextColor(Color.BLACK)
                }
                RadarView.Theme.RED_NIGHT -> {
                    holder.avatarBg.setBackgroundResource(R.drawable.status_box_bg_red)
                    holder.avatarText.setTextColor(Color.BLACK)
                }
                RadarView.Theme.PINK -> {
                    holder.avatarBg.setBackgroundResource(R.drawable.status_box_bg_pink)
                    holder.avatarText.setTextColor(Color.BLACK)
                }
                RadarView.Theme.NEON -> {
                    holder.avatarBg.setBackgroundResource(R.drawable.status_box_bg_neon)
                    holder.avatarText.setTextColor(Color.BLACK)
                }
                RadarView.Theme.NARANJA -> {
                    holder.avatarBg.setBackgroundResource(R.drawable.status_box_bg_naranja)
                    holder.avatarText.setTextColor(Color.BLACK)
                }
                RadarView.Theme.BUBBLEGUM -> {
                    holder.avatarBg.setBackgroundResource(R.drawable.status_box_bg_bubblegum)
                    holder.avatarText.setTextColor(Color.parseColor("#00FDFF"))
                }
                RadarView.Theme.SUMMERTIME -> {
                    holder.avatarBg.setBackgroundResource(R.drawable.status_box_bg_naranja)
                    holder.avatarText.setTextColor(Color.BLACK)
                }
                else -> {
                    holder.avatarBg.setBackgroundResource(R.drawable.status_box_bg_black)
                    holder.avatarText.setTextColor(Color.parseColor("#00FF41"))
                }
            }

            holder.statusIndicator.visibility = View.VISIBLE
            
            if (isOnline) {
                holder.statusIndicator.setImageResource(R.drawable.ic_online)
                holder.statusIndicator.alpha = 1.0f
                val blink = android.view.animation.AlphaAnimation(1.0f, 0.4f)
                blink.duration = 800
                blink.repeatCount = android.view.animation.Animation.INFINITE
                blink.repeatMode = android.view.animation.Animation.REVERSE
                holder.statusIndicator.startAnimation(blink)
            } else {
                holder.statusIndicator.clearAnimation()
                holder.statusIndicator.setImageResource(R.drawable.ic_offline)
                holder.statusIndicator.alpha = 0.5f
            }
            
            // Unread dot
            val unreadDot: View = holder.itemView.findViewById(R.id.viewUnreadIndicator)
            unreadDot.visibility = if (device.seenCount > 0) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onClick(device) }
            holder.itemView.setOnLongClickListener {
                onLongClick(device)
                true
            }
            holder.btnDelete.setOnClickListener { onDeleteClick(device) }
        }

        override fun getItemCount() = devices.size
    }
}
