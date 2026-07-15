package com.radar.blewifi

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.TextView
import java.util.*

class RadarApplication : Application() {

    private var currentActivity: Activity? = null
    lateinit var scanner: ScannerManager
    lateinit var nearbyManager: NearbyManager
    
    private val handler = Handler(Looper.getMainLooper())
    private val sentMessages = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())

    fun recordSentMessage(text: String) {
        sentMessages.add(text to System.currentTimeMillis())
        val now = System.currentTimeMillis()
        sentMessages.removeAll { now - it.second > 30000 } // Keep for 30s
    }

    private fun isDuplicateOfSentMessage(text: String): Boolean {
        val now = System.currentTimeMillis()
        sentMessages.removeAll { now - it.second > 30000 }
        
        return sentMessages.any { (sentText, _) ->
            // Exact match or prefix match (for BLE truncation)
            sentText == text || 
            (text.length >= 10 && sentText.startsWith(text)) || 
            (sentText.length >= 10 && text.startsWith(sentText))
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        scanner = ScannerManager(this)
        
        val prefs = getSharedPreferences("pager_prefs", MODE_PRIVATE)
        val myName = prefs.getString("user_id", "UNIT_${(100..999).random()}") ?: "UNIT_000"
        nearbyManager = NearbyManager(this, myName)

        setupMessaging()
        setupActivityListener()
        
        scanner.startScanning(true)
        nearbyManager.start()
    }

    private fun setupMessaging() {
        nearbyManager.addListener(object : NearbyManager.NearbyListener {
            override fun onMessageReceived(senderCallsign: String, text: String) {
                handleNewMessage(senderCallsign, text)
            }

            override fun onVoiceMessageReceived(senderCallsign: String, filePath: String) {
                // Handle voice message in background
                saveVoiceMessageToHistory(senderCallsign, filePath)
            }

            override fun onFileReceived(senderCallsign: String, filePath: String, fileName: String) {
                // TODO: Handle file in background if needed
                saveFileToHistory(senderCallsign, filePath, fileName)
            }

            override fun onDeviceFound(callsign: String) {}
            override fun onDeviceLost(callsign: String) {}
        })

        scanner.addListener(object : ScannerManager.ScanListener {
            private val processedMessageIds = mutableSetOf<String>()
            private val lastReceivedTimestamps = mutableMapOf<String, Long>()

            override fun onDevicesUpdated(devices: List<ScanDevice>) {
                val myName = getSharedPreferences("pager_prefs", MODE_PRIVATE).getString("user_id", "")
                devices.filter { it.type == DeviceType.PAGER && it.displayName != myName }.forEach { device ->
                    val chatName = device.displayName
                    val lastSeenTime = lastReceivedTimestamps[chatName] ?: 0L
                    
                    if (device.lastMessageTime > lastSeenTime && device.lastMessage != null) {
                        var rawMessage = device.lastMessage ?: ""
                        var msgId: String? = null
                        var displayMessage = rawMessage

                        if (rawMessage.startsWith("ID:")) {
                            val parts = rawMessage.split("|", limit = 2)
                            if (parts.size == 2) {
                                msgId = "${chatName}_${parts[0]}"
                                displayMessage = parts[1]
                            }
                        }
                        
                        val finalMsgId = msgId ?: "${chatName}_${displayMessage.hashCode()}_${device.lastMessageTime}"

                        if (!processedMessageIds.contains(finalMsgId)) {
                            processedMessageIds.add(finalMsgId)
                            lastReceivedTimestamps[chatName] = device.lastMessageTime
                            handleNewMessage(chatName, displayMessage)
                        }
                    }
                }
            }
            override fun onScanStatusChanged(scanning: Boolean) {}
            override fun onMovementDetected(device: ScanDevice) {}
            override fun onLocationUpdated(lat: Double, lon: Double) {}
            override fun onError(error: String) {}
        })
    }

    private fun handleNewMessage(sender: String, text: String) {
        val myName = getSharedPreferences("pager_prefs", MODE_PRIVATE).getString("user_id", "") ?: ""
        if (sender == myName || (myName.isNotEmpty() && sender.contains(myName))) return // Don't notify for own messages
        
        // Filter out echoes of messages we just sent (including targeted prefix versions)
        if (isDuplicateOfSentMessage(text)) return

        var finalMessage = text
        // If message has a target prefix (@CALLSIGN:), check it
        if (text.startsWith("@")) {
            val parts = text.split(":", limit = 2)
            if (parts.size == 2) {
                val target = parts[0].substring(1) // remove @
                if (target != myName) {
                    // Even if not for me, check if I sent it as a broadcast echo
                    return 
                }
                finalMessage = parts[1]
            }
        }
        
        // Final check on the message content itself (after stripping @prefix if it was for me)
        if (isDuplicateOfSentMessage(finalMessage)) return

        // 1. Save to history
        saveMessageToHistory(sender, finalMessage)
        
        // 2. Notify PagerActivity if it's open
        val isPagerOpen = currentActivity is PagerActivity
        (currentActivity as? PagerActivity)?.let {
            it.runOnUiThread { it.onDevicesUpdated(scanner.getDevices()) }
        }

        // Only notify if there are no current unread messages and we're not already in the Pager
        val prefs = getSharedPreferences("pager_history", MODE_PRIVATE)
        val alreadyHasUnread = prefs.getBoolean("has_unread", false)

        if (!alreadyHasUnread && !isPagerOpen) {
            // 3. Visual & Haptic effects
            handler.post {
                triggerGlobalEffects(sender, finalMessage)
            }

            // 4. Update unread status and notify MainActivity
            prefs.edit().putBoolean("has_unread", true).apply()
            sendBroadcast(Intent("com.radar.blewifi.ACTION_NEW_MESSAGE"))
        }
    }

    private fun saveMessageToHistory(sender: String, text: String) {
        val prefs = getSharedPreferences("pager_history", MODE_PRIVATE)
        val json = prefs.getString("all_messages", "{}")
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, MutableList<PagerActivity.ChatMessage>>>() {}.type
        val allMessages: MutableMap<String, MutableList<PagerActivity.ChatMessage>> = gson.fromJson(json, type) ?: mutableMapOf()
        
        val msg = PagerActivity.ChatMessage(UUID.randomUUID().toString(), sender, text, System.currentTimeMillis(), false)
        allMessages.getOrPut(sender) { mutableListOf() }.add(msg)
        
        prefs.edit().putString("all_messages", gson.toJson(allMessages)).apply()
    }

    private fun saveFileToHistory(sender: String, filePath: String, fileName: String) {
        val prefs = getSharedPreferences("pager_history", MODE_PRIVATE)
        val json = prefs.getString("all_messages", "{}")
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, MutableList<PagerActivity.ChatMessage>>>() {}.type
        val allMessages: MutableMap<String, MutableList<PagerActivity.ChatMessage>> = gson.fromJson(json, type) ?: mutableMapOf()

        val msg = PagerActivity.ChatMessage(UUID.randomUUID().toString(), sender, "[FILE: $fileName]", System.currentTimeMillis(), false, filePath = filePath, fileName = fileName)
        allMessages.getOrPut(sender) { mutableListOf() }.add(msg)

        prefs.edit().putString("all_messages", gson.toJson(allMessages)).apply()
    }

    private fun saveVoiceMessageToHistory(sender: String, filePath: String) {
        val prefs = getSharedPreferences("pager_history", MODE_PRIVATE)
        val json = prefs.getString("all_messages", "{}")
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, MutableList<PagerActivity.ChatMessage>>>() {}.type
        val allMessages: MutableMap<String, MutableList<PagerActivity.ChatMessage>> = gson.fromJson(json, type) ?: mutableMapOf()

        val msg = PagerActivity.ChatMessage(UUID.randomUUID().toString(), sender, "[VOICE_MAIL]", System.currentTimeMillis(), false, audioPath = filePath)
        allMessages.getOrPut(sender) { mutableListOf() }.add(msg)

        prefs.edit().putString("all_messages", gson.toJson(allMessages)).apply()
    }

    private fun triggerGlobalEffects(sender: String, text: String) {
        val activity = currentActivity ?: return
        
        // Vibrate
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))

        // Glitch Effect
        showGlitchOverlay(activity)
        
        // Message Bubble
        showMessageBubble(activity, sender, text)
    }

    private fun showGlitchOverlay(activity: Activity) {
        val root = activity.window.decorView as ViewGroup
        val overlay = View(activity)
        overlay.setBackgroundColor(Color.parseColor("#00FF41"))
        overlay.alpha = 0.4f
        root.addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        var frames = 0
        val glitchAction = object : Runnable {
            override fun run() {
                if (frames > 2) { // Flash once: Green frame, Pink frame, then Done
                    root.removeView(overlay)
                    activity.window.decorView.translationX = 0f
                    activity.window.decorView.translationY = 0f
                    return
                }

                val isGreen = frames % 2 == 0
                overlay.setBackgroundColor(if (isGreen) Color.parseColor("#00FF41") else Color.parseColor("#FF00FF"))
                overlay.alpha = 0.5f
                
                activity.window.decorView.translationX = ((-15..15).random()).toFloat()
                activity.window.decorView.translationY = ((-10..10).random()).toFloat()

                frames++
                handler.postDelayed(this, 100)
            }
        }
        handler.post(glitchAction)
    }

    private fun showMessageBubble(activity: Activity, sender: String, text: String) {
        val root = activity.window.decorView as ViewGroup
        
        val bubbleContainer = FrameLayout(activity)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.TOP
        params.topMargin = 100
        params.leftMargin = 40
        params.rightMargin = 40
        
        val bubble = TextView(activity)
        bubble.text = ">> NEW_MSG: $sender\n${text.take(40)}${if(text.length > 40) "..." else ""}"
        bubble.setTextColor(Color.parseColor("#00FF41"))
        bubble.setBackgroundResource(R.drawable.status_box_bg_black)
        bubble.setPadding(30, 20, 30, 20)
        bubble.typeface = android.graphics.Typeface.MONOSPACE
        bubble.setShadowLayer(5f, 0f, 0f, Color.parseColor("#00FF41"))
        
        bubbleContainer.addView(bubble)
        root.addView(bubbleContainer, params)
        
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.startOffset = 3500
        fadeOut.duration = 500
        fadeOut.fillAfter = true
        
        bubbleContainer.startAnimation(fadeOut)
        
        handler.postDelayed({
            root.removeView(bubbleContainer)
        }, 4000)
        
        bubbleContainer.setOnClickListener {
            val intent = Intent(activity, PagerActivity::class.java)
            intent.putExtra("CHAT_ID", sender)
            activity.startActivity(intent)
            root.removeView(bubbleContainer)
        }
    }

    private fun setupActivityListener() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) currentActivity = null
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
