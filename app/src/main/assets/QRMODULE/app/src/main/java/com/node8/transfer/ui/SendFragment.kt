package com.node8.transfer.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.node8.transfer.databinding.FragmentSendBinding
import com.node8.transfer.protocol.Container
import com.node8.transfer.protocol.FountainEncoder
import com.node8.transfer.protocol.FrameCodec
import com.node8.transfer.protocol.QrCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

class SendFragment : Fragment() {

    private var _binding: FragmentSendBinding? = null
    private val binding get() = _binding!!

    private val fpsOptions = listOf(10, 15, 20, 24, 30)
    private val bytesOptions = listOf(500, 1000, 1250, 1500, 2000, 2500)
    private val eccOptions = listOf(
        "L" to ErrorCorrectionLevel.L,
        "M" to ErrorCorrectionLevel.M,
        "Q" to ErrorCorrectionLevel.Q,
        "H" to ErrorCorrectionLevel.H
    )
    private val gridOptions = listOf(
        "1 code" to 1, "2 codes (1x2)" to 2, "4 codes (2x2)" to 4, "6 codes (2x3)" to 6
    )

    private lateinit var qrViews: List<ImageView>
    private var animJob: Job? = null
    private var payloadBytes: ByteArray? = null
    private var payloadName: String = "file.bin"
    private var payloadType: String = "application/octet-stream"

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { loadFile(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        qrViews = listOf(binding.qr0, binding.qr1, binding.qr2, binding.qr3, binding.qr4, binding.qr5)

        binding.spinFps.adapter = simpleAdapter(fpsOptions.map { "$it" })
        binding.spinFps.setSelection(fpsOptions.indexOf(20).coerceAtLeast(0))

        binding.spinBytes.adapter = simpleAdapter(bytesOptions.map { "$it" })
        binding.spinBytes.setSelection(1) // Default to 1000 bytes, which is safer

        binding.spinEcc.adapter = simpleAdapter(eccOptions.map { it.first })

        binding.spinGrid.adapter = simpleAdapter(gridOptions.map { it.first })

        binding.modePicker.setOnCheckedChangeListener { _, checkedId ->
            val isFile = checkedId == binding.radioFile.id
            binding.paneFile.visibility = if (isFile) View.VISIBLE else View.GONE
            binding.paneSnippet.visibility = if (isFile) View.GONE else View.VISIBLE
        }

        binding.btnPickFile.setOnClickListener { pickFileLauncher.launch("*/*") }

        binding.btnStartSnippet.setOnClickListener {
            val text = binding.snippetText.text.toString()
            if (text.isNotBlank()) {
                payloadBytes = text.toByteArray(Charsets.UTF_8)
                payloadName = "snippet.txt"
                payloadType = "text/plain"
                startTransfer()
            }
        }

        binding.btnStop.setOnClickListener { stopTransfer() }
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun loadFile(uri: Uri) {
        val resolver = requireContext().contentResolver
        var name = "file.bin"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        val maxBytes = 64L * 1024 * 1024
        if (size > maxBytes) {
            binding.statusLine.text = "File too large (max 64 MB)"
            return
        }
        val type = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            binding.statusLine.text = "Could not read file"
            return
        }
        payloadBytes = bytes
        payloadName = name
        payloadType = type
        binding.fileLabel.text = "$name · ${bytes.size / 1024} KB"
        startTransfer()
    }

    private fun startTransfer() {
        val data = payloadBytes ?: return
        stopTransfer()

        val fps = fpsOptions[binding.spinFps.selectedItemPosition.coerceAtLeast(0)]
        val blockSize = bytesOptions[binding.spinBytes.selectedItemPosition.coerceAtLeast(0)]
        val ecLevel = eccOptions[binding.spinEcc.selectedItemPosition.coerceAtLeast(0)].second
        val gridCount = gridOptions[binding.spinGrid.selectedItemPosition.coerceAtLeast(0)].second
        val cols = when (gridCount) {
            1 -> 1
            2 -> 2
            4 -> 2
            6 -> 3
            else -> 2
        }

        val container = Container.build(payloadName, payloadType, data, compress = true)
        val transferId = Random().nextInt(0xFFFF)
        val encoder = FountainEncoder(container, blockSize, transferId)

        binding.stage.columnCount = cols
        binding.stage.visibility = View.VISIBLE
        binding.btnStop.visibility = View.VISIBLE
        qrViews.forEachIndexed { i, iv -> iv.visibility = if (i < gridCount) View.VISIBLE else View.GONE }
        binding.statusLine.text =
            "Sending ${payloadName} · ${data.size / 1024} KB · ${encoder.k} blocks · $gridCount code(s)/frame"

        val frameDelayMs = (1000L / fps).coerceAtLeast(16L)
        var framesSent = 0

        animJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                while (isActive) {
                    val bitmaps = withContext(Dispatchers.Default) {
                        (0 until gridCount).map {
                            val sym = encoder.nextSymbol()
                            val packed = FrameCodec.pack(sym)
                            QrCodec.encode(packed, ecLevel, 512)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (!isActive) return@withContext
                        for (i in 0 until gridCount) {
                            qrViews[i].setImageBitmap(bitmaps[i])
                        }
                        framesSent++
                        binding.statusLine.text =
                            "Sending ${payloadName} · ${encoder.k} blocks · $framesSent frame(s) sent"
                    }
                    delay(frameDelayMs)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusLine.text = "Error: ${e.message}"
                    stopTransfer()
                }
            }
        }
    }

    private fun stopTransfer() {
        animJob?.cancel()
        animJob = null
    }

    override fun onDestroyView() {
        stopTransfer()
        super.onDestroyView()
        _binding = null
    }
}
