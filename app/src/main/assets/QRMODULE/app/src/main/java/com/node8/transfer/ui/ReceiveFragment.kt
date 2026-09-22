package com.node8.transfer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.node8.transfer.camera.QrAnalyzer
import com.node8.transfer.databinding.FragmentReceiveBinding
import com.node8.transfer.protocol.Container
import com.node8.transfer.protocol.FountainDecoder
import com.node8.transfer.protocol.FountainSymbol
import com.node8.transfer.protocol.FrameCodec
import java.io.File
import java.util.concurrent.Executors

class ReceiveFragment : Fragment() {

    private var _binding: FragmentReceiveBinding? = null
    private val binding get() = _binding!!

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var currentTransferId: Int? = null
    @Volatile private var decoder: FountainDecoder? = null
    @Volatile private var completed = false
    private var startTimeMs: Long = 0
    private var framesNew = 0
    private var framesDup = 0
    private var savedFile: File? = null

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else binding.statsLine.text = "Camera permission denied"
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReceiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnStartCamera.setOnClickListener {
            resetState()
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            } else {
                requestPermission.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnOpenFile.setOnClickListener { shareSavedFile() }
    }

    private fun resetState() {
        currentTransferId = null
        decoder = null
        framesNew = 0
        framesDup = 0
        completed = false
        savedFile = null
        binding.resultImage.visibility = View.GONE
        binding.resultText.visibility = View.GONE
        binding.resultDone.visibility = View.GONE
        binding.btnOpenFile.visibility = View.GONE
        binding.progressBar.progress = 0
        binding.progressLabel.text = "0% · 0 frames"
    }

    private fun startCamera() {
        binding.previewZone.visibility = View.VISIBLE
        binding.btnStartCamera.visibility = View.GONE
        binding.statsLine.text = "Scanning…"
        startTimeMs = System.currentTimeMillis()

        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, QrAnalyzer { payloads -> onPayloads(payloads) })

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                binding.statsLine.text = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun onPayloads(payloads: List<ByteArray>) {
        if (completed) return
        var newlyUseful = false
        for (raw in payloads) {
            val sym = FrameCodec.unpack(raw) ?: continue

            if (currentTransferId != null && currentTransferId != sym.transferId) {
                // A different transfer started; drop old progress and follow the new one.
                resetDecoderFor(sym)
            } else if (decoder == null) {
                resetDecoderFor(sym)
            }

            val d = decoder ?: continue
            val added = d.addSymbol(sym)
            if (added) {
                newlyUseful = true
                framesNew++
            } else {
                framesDup++
            }
        }

        if (newlyUseful) {
            requireActivity().runOnUiThread { updateProgress() }
        }
    }

    private fun resetDecoderFor(sym: FountainSymbol) {
        currentTransferId = sym.transferId
        decoder = FountainDecoder(sym.k, sym.blockSize)
        framesNew = 0
        framesDup = 0
        startTimeMs = System.currentTimeMillis()
    }

    private fun updateProgress() {
        val d = decoder ?: return
        val pct = (d.recoveredBlocks * 100 / d.k.coerceAtLeast(1))
        binding.progressLabel.text = "$pct% · ${d.recoveredBlocks}/${d.k} blocks"
        binding.progressBar.progress = pct

        val elapsedS = ((System.currentTimeMillis() - startTimeMs) / 1000.0).coerceAtLeast(0.1)
        val fps = "%.1f".format(framesNew / elapsedS)
        binding.diagnostics.text =
            "elapsed: %.1fs  |  frames new/dup: %d/%d  |  decode fps: %s  |  blocks K: %d"
                .format(elapsedS, framesNew, framesDup, fps, d.k)

        if (d.isComplete) {
            completed = true
            finishTransfer(d)
        }
    }

    private fun finishTransfer(d: FountainDecoder) {
        val assembled = d.assemble() ?: return
        val parsed = Container.parse(assembled)
        if (parsed == null || !parsed.verified) {
            binding.resultDone.text = "Transfer failed integrity check"
            binding.resultDone.visibility = View.VISIBLE
            binding.statsLine.text = "Failed — tap Start camera to retry"
            return
        }

        binding.statsLine.text = "Received ${parsed.name}"
        val outDir = File(requireContext().getExternalFilesDir(null), "received").apply { mkdirs() }
        val outFile = File(outDir, parsed.name)
        outFile.writeBytes(parsed.bytes)
        savedFile = outFile

        when {
            parsed.type.startsWith("image/") -> {
                val bmp: Bitmap? = BitmapFactory.decodeByteArray(parsed.bytes, 0, parsed.bytes.size)
                if (bmp != null) {
                    binding.resultImage.setImageBitmap(bmp)
                    binding.resultImage.visibility = View.VISIBLE
                }
            }
            parsed.type.startsWith("text/") -> {
                binding.resultText.text = String(parsed.bytes, Charsets.UTF_8)
                binding.resultText.visibility = View.VISIBLE
            }
        }
        binding.resultDone.text = "Done — ${parsed.name} (${parsed.bytes.size / 1024} KB)"
        binding.resultDone.visibility = View.VISIBLE
        binding.btnOpenFile.visibility = View.VISIBLE
    }

    private fun shareSavedFile() {
        val file = savedFile ?: return
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = requireContext().contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open / share received file"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
