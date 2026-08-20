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

            val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
            val isRedNight = theme == RadarView.Theme.RED_NIGHT
            val isPink = theme == RadarView.Theme.PINK
            val isNeon = theme == RadarView.Theme.NEON
            val isNaranja = theme == RadarView.Theme.NARANJA
            val isBubblegum = theme == RadarView.Theme.BUBBLEGUM
            val isSummertime = theme == RadarView.Theme.SUMMERTIME

            when {
                isHighContrast -> {
                    holder.itemView.setBackgroundColor(Color.parseColor("#EEEEEE"))
                    holder.title.setTextColor(Color.BLACK)
                    holder.count.setTextColor(Color.DKGRAY)
                    holder.indicator.setColorFilter(Color.BLACK)
                }
                isRedNight -> {
                    holder.itemView.setBackgroundColor(Color.parseColor("#1A0000"))
                    holder.title.setTextColor(Color.RED)
                    holder.count.setTextColor(Color.parseColor("#990000"))
                    holder.indicator.setColorFilter(Color.RED)
                }
                isPink -> {
                    holder.itemView.setBackgroundColor(Color.parseColor("#330033"))
                    holder.title.setTextColor(Color.parseColor("#FF00FF"))
                    holder.count.setTextColor(Color.parseColor("#990099"))
                    holder.indicator.setColorFilter(Color.parseColor("#FF00FF"))
                }
                isNeon -> {
                    holder.itemView.setBackgroundColor(Color.parseColor("#333300"))
                    holder.title.setTextColor(Color.parseColor("#E6FB04"))
                    holder.count.setTextColor(Color.parseColor("#999900"))
                    holder.indicator.setColorFilter(Color.parseColor("#E6FB04"))
                }
                isNaranja -> {
                    holder.itemView.setBackgroundColor(Color.parseColor("#331E00"))
                    holder.title.setTextColor(Color.parseColor("#FF8C00"))
                    holder.count.setTextColor(Color.parseColor("#B36200"))
                    holder.indicator.setColorFilter(Color.parseColor("#FF8C00"))
                }
                isBubblegum -> {
                    holder.itemView.setBackgroundColor(Color.parseColor("#1A001A"))
                    holder.title.setTextColor(Color.parseColor("#FF00FF"))
                    holder.count.setTextColor(Color.parseColor("#00FDFF"))
                    holder.indicator.setColorFilter(Color.parseColor("#00FDFF"))
                }
                isSummertime -> {
                    holder.itemView.setBackgroundColor(Color.parseColor("#2A1F1A"))
                    holder.title.setTextColor(Color.parseColor("#ff9f6b"))
                    holder.count.setTextColor(Color.parseColor("#6befff"))
                    holder.indicator.setColorFilter(Color.parseColor("#6befff"))
                }
                else -> {
                    holder.itemView.setBackgroundColor(Color.parseColor("#0F0F0F"))
                    holder.title.setTextColor(Color.parseColor("#00FF41"))
                    holder.count.setTextColor(Color.GRAY)
                    holder.indicator.setColorFilter(Color.parseColor("#00FF41"))
                }
            }

            holder.itemView.setOnClickListener { toggleCategory(category) }
        } else if (holder is DeviceViewHolder) {
            val device = displayList[position] as ScanDevice
            bindDevice(holder, device)
        }
    }

    private fun bindDevice(holder: DeviceViewHolder, device: ScanDevice) {
        holder.name.text = device.displayName
        
        val isWep = device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")
        val isWhisper = device.isVulnerableWhisperPair

        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = theme == RadarView.Theme.RED_NIGHT
        val isPink = theme == RadarView.Theme.PINK
        val isNeon = theme == RadarView.Theme.NEON
        val isNaranja = theme == RadarView.Theme.NARANJA
        val isBubblegum = theme == RadarView.Theme.BUBBLEGUM
        val isSummertime = theme == RadarView.Theme.SUMMERTIME
        
        val nameColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#FF00FF")
            isSummertime -> Color.parseColor("#ff9f6b")
            device.type == DeviceType.BLE -> Color.parseColor("#0066FF")
            device.type == DeviceType.AIRCRAFT -> Color.parseColor("#00FFFF")
            else -> {
                val caps = device.capabilities.uppercase()
                val is5GHz = device.frequency in 5000..6000
                when {
                    is5GHz -> Color.parseColor("#008F22")
                    caps.contains("WPA") -> Color.parseColor("#00FF41")
                    else -> Color.WHITE
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
            if (isSummertime) holder.mfrLogo.setColorFilter(Color.parseColor("#ff9f6b")) else holder.mfrLogo.clearColorFilter()
        } else if (isAndroid) {
            holder.mfrLogo.visibility = View.VISIBLE
            holder.mfrLogo.setImageResource(when {
                isHighContrast -> R.drawable.ic_android_black
                isRedNight -> R.drawable.ic_android_red
                else -> R.drawable.ic_android
            })
            if (isSummertime) holder.mfrLogo.setColorFilter(Color.parseColor("#ff9f6b")) else holder.mfrLogo.clearColorFilter()
        } else {
            holder.mfrLogo.visibility = View.GONE
        }
        
        // RSSI coloring for archive items
        holder.dist.setTextColor(when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.parseColor("#990000")
            RadarView.Theme.PINK -> Color.parseColor("#990099")
            RadarView.Theme.NEON -> Color.parseColor("#B3C403")
            RadarView.Theme.NARANJA -> Color.parseColor("#995400")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#6befff")
            else -> Color.parseColor("#FFB300")
        })
        holder.dist.setShadowLayer(0f, 0f, 0f, 0)
        holder.dist.clearAnimation()
        holder.dist.alpha = 1.0f

        holder.addr.text = device.address
        holder.addr.setTextColor(when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.DKGRAY
            RadarView.Theme.RED_NIGHT -> Color.parseColor("#660000")
            RadarView.Theme.PINK -> Color.parseColor("#660066")
            RadarView.Theme.NEON -> Color.parseColor("#666600")
            RadarView.Theme.NARANJA -> Color.parseColor("#663B00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#660066")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#666befff")
            else -> Color.GRAY
        })

        // Vulnerability Badges
        holder.alarm.visibility = if (isWep) View.VISIBLE else View.GONE
        holder.alarm.setTextColor(if (isHighContrast) Color.BLACK else Color.RED)
        
        holder.whisper.visibility = if (isWhisper) View.VISIBLE else View.GONE
        holder.whisper.setTextColor(when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isSummertime -> Color.parseColor("#6befff")
            else -> Color.parseColor("#FF00FF")
        })
        
        holder.airtag.visibility = if (device.isAirTag) View.VISIBLE else View.GONE
        holder.airtag.setTextColor(when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isSummertime -> Color.parseColor("#ff9f6b")
            else -> Color.WHITE
        })
        
        holder.aero.visibility = if (device.type == DeviceType.AIRCRAFT) View.VISIBLE else View.GONE
        holder.aero.setTextColor(when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isSummertime -> Color.parseColor("#6befff")
            else -> Color.CYAN
        })
        
        holder.car.visibility = if (device.isCar) View.VISIBLE else View.GONE
        holder.car.setTextColor(when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isSummertime -> Color.parseColor("#6befff")
            else -> Color.parseColor("#FF00FF")
        })
        
        holder.hijack.visibility = if (device.isPubliclyConnectable) View.VISIBLE else View.GONE
        holder.hijack.setTextColor(when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isSummertime -> Color.parseColor("#6befff")
            else -> Color.YELLOW
        })

        // Extra Vulns
        val extraVulns = mutableListOf<String>()
        if (device.isLegacyBluetooth) extraVulns.add("LEGACY")
        if (device.isVulnerableBlueWhisper) extraVulns.add("BW")
        if (device.isVulnerableCVE202536911) extraVulns.add("CVE-2025-36911")
        
        if (extraVulns.isNotEmpty()) {
            holder.vuln.visibility = View.VISIBLE
            holder.vuln.text = extraVulns.joinToString(" | ")
            holder.vuln.setTextColor(when (theme) {
                RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
                RadarView.Theme.RED_NIGHT -> Color.RED
                RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
                RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
                RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
                RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
                RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
                else -> Color.YELLOW
            })
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
