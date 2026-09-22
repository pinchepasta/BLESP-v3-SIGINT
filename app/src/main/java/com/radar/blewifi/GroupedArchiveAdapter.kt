package com.radar.blewifi

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ConcurrentHashMap

class GroupedArchiveAdapter(private val onDeviceClick: (ScanDevice) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var displayList: List<Any> = emptyList() // Can be String (Header) or ScanDevice (Item)
    private var expandedCategories = ConcurrentHashMap<String, Boolean>()
    private var allGroupedDevices = mapOf<String, List<ScanDevice>>()
    var theme: RadarView.Theme = RadarView.Theme.DEFAULT

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    fun submitData(groupedDevices: Map<String, List<ScanDevice>>) {
        allGroupedDevices = groupedDevices
        // Default all to COLLAPSED if first time or data reset
        if (expandedCategories.isEmpty()) {
            groupedDevices.keys.forEach { expandedCategories[it] = false }
        }
        updateDisplayList()
    }

    private fun updateDisplayList() {
        val newList = mutableListOf<Any>()
        allGroupedDevices.keys.sorted().forEach { category ->
            newList.add(category)
            if (expandedCategories[category] == true) {
                newList.addAll(allGroupedDevices[category] ?: emptyList())
            }
        }
        displayList = newList
        notifyDataSetChanged()
    }

    private fun toggleCategory(category: String) {
        val current = expandedCategories[category] ?: false
        expandedCategories[category] = !current
        updateDisplayList()
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is String) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_archive_header, parent, false))
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
            DeviceViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            val category = displayList[position] as String
            holder.title.text = category.uppercase()
            val count = allGroupedDevices[category]?.size ?: 0
            holder.count.text = "$count"
            
            val isExpanded = expandedCategories[category] == true
            holder.indicator.rotation = if (isExpanded) 0f else -90f

            val colors = ThemeUtils.getThemeColors(theme)
            val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST

            holder.itemView.setBackgroundColor(colors.header)
            holder.title.setTextColor(colors.primary)
            holder.count.setTextColor(colors.dim)
            holder.indicator.setColorFilter(if (isHighContrast) Color.BLACK else colors.primary)

            holder.itemView.setOnClickListener { toggleCategory(category) }
        } else if (holder is DeviceViewHolder) {
            val device = displayList[position] as ScanDevice
            bindDevice(holder, device)
        }
    }

    private fun bindDevice(holder: DeviceViewHolder, device: ScanDevice) {
        holder.name.text = device.displayName
        
        val colors = ThemeUtils.getThemeColors(theme)
        val isWep = device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")
        val isWhisper = device.isVulnerableWhisperPair

        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = theme == RadarView.Theme.RED_NIGHT
        
        val nameColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            device.type == DeviceType.BLE -> colors.primary
            device.type == DeviceType.AIRCRAFT -> colors.accent
            else -> {
                val caps = device.capabilities.uppercase()
                val is5GHz = device.frequency in 5000..6000
                when {
                    is5GHz -> colors.dim
                    caps.contains("WPA") -> colors.primary
                    else -> colors.primary
                }
            }
        }
        
        holder.name.setTextColor(nameColor)
        holder.dist.text = device.distanceLabel
        
        // Manufacturer Logos
        val isApple = device.manufacturer == "0x004C" || device.name.lowercase().contains("apple") || device.isAirTag
        val isAndroid = device.manufacturer == "0x00E0" || device.name.lowercase().contains("android") || device.isFastPair
        
        if (isApple) {
            holder.mfrLogo.visibility = View.VISIBLE
            holder.mfrLogo.setImageResource(when {
                isHighContrast -> R.drawable.ic_apple_black
                isRedNight -> R.drawable.ic_apple_red
                else -> R.drawable.ic_apple
            })
            if (!isHighContrast && !isRedNight) holder.mfrLogo.setColorFilter(colors.primary) else holder.mfrLogo.clearColorFilter()
        } else if (isAndroid) {
            holder.mfrLogo.visibility = View.VISIBLE
            holder.mfrLogo.setImageResource(when {
                isHighContrast -> R.drawable.ic_android_black
                isRedNight -> R.drawable.ic_android_red
                else -> R.drawable.ic_android
            })
            if (!isHighContrast && !isRedNight) holder.mfrLogo.setColorFilter(colors.primary) else holder.mfrLogo.clearColorFilter()
        } else {
            holder.mfrLogo.visibility = View.GONE
        }
        
        // RSSI coloring for archive items
        holder.dist.setTextColor(if (isHighContrast) Color.BLACK else colors.accent)
        holder.dist.setShadowLayer(0f, 0f, 0f, 0)
        holder.dist.clearAnimation()
        holder.dist.alpha = 1.0f

        holder.addr.text = device.address
        holder.addr.setTextColor(colors.dim)

        // Vulnerability Badges
        holder.alarm.visibility = if (isWep) View.VISIBLE else View.GONE
        holder.alarm.setTextColor(if (isHighContrast) Color.BLACK else colors.primary)
        
        holder.whisper.visibility = if (isWhisper) View.VISIBLE else View.GONE
        holder.whisper.setTextColor(if (isHighContrast) Color.BLACK else colors.accent)
        
        holder.airtag.visibility = if (device.isAirTag) View.VISIBLE else View.GONE
        holder.airtag.setTextColor(if (isHighContrast) Color.BLACK else colors.primary)
        
        holder.aero.visibility = if (device.type == DeviceType.AIRCRAFT) View.VISIBLE else View.GONE
        holder.aero.setTextColor(if (isHighContrast) Color.BLACK else colors.accent)
        
        holder.car.visibility = if (device.isCar) View.VISIBLE else View.GONE
        holder.car.setTextColor(if (isHighContrast) Color.BLACK else colors.primary)
        
        holder.hijack.visibility = if (device.isPubliclyConnectable) View.VISIBLE else View.GONE
        holder.hijack.setTextColor(if (isHighContrast) Color.BLACK else colors.accent)

        // Extra Vulns
        val extraVulns = mutableListOf<String>()
        if (device.isLegacyBluetooth) extraVulns.add("LEGACY")
        if (device.isVulnerableBlueWhisper) extraVulns.add("BW")
        if (device.isVulnerableCVE202536911) extraVulns.add("CVE-2025-36911")
        
        if (extraVulns.isNotEmpty()) {
            holder.vuln.visibility = View.VISIBLE
            holder.vuln.text = extraVulns.joinToString(" | ")
            holder.vuln.setTextColor(if (isHighContrast) Color.BLACK else colors.primary)
        } else {
            holder.vuln.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener { onDeviceClick(device) }
    }

    override fun getItemCount() = displayList.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvCategoryName)
        val count: TextView = view.findViewById(R.id.tvCategoryCount)
        val indicator: ImageView = view.findViewById(R.id.ivExpandIndicator)
    }

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvDeviceName)
        val dist: TextView = view.findViewById(R.id.tvDeviceDist)
        val addr: TextView = view.findViewById(R.id.tvDeviceAddr)
        val alarm: TextView = view.findViewById(R.id.tvAlarm)
        val whisper: TextView = view.findViewById(R.id.tvWhisper)
        val vuln: TextView = view.findViewById(R.id.tvVulnerability)
        val airtag: TextView = view.findViewById(R.id.tvAirTag)
        val aero: TextView = view.findViewById(R.id.tvAeroIcon)
        val car: TextView = view.findViewById(R.id.tvCarIcon)
        val hijack: TextView = view.findViewById(R.id.tvHijackIcon)
        val mfrLogo: ImageView = view.findViewById(R.id.ivManufacturerLogo)
    }
}
