package com.mostlyawesome.tagtinker

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mostlyawesome.tagtinker.data.*
import com.mostlyawesome.tagtinker.utils.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    // ── Theme ────────────────────────────────────────────────────────────────
    var themeSelection by mutableStateOf<Boolean?>(null)

    // ── ESL Target ──────────────────────────────────────────────────────────
    var eslTarget by mutableStateOf(EslTarget())
        private set

    var plidHex   by mutableStateOf("—")
        private set
    var crcHex    by mutableStateOf("—")
        private set

    init { recalcPlid() }

    fun updateBarcode(v: String)   { eslTarget = eslTarget.copy(barcode  = v); recalcPlid() }
    fun updateEslWidth(v: Int)     { eslTarget = eslTarget.copy(widthPx  = v.coerceIn(50, 800)) }
    fun updateEslHeight(v: Int)    { eslTarget = eslTarget.copy(heightPx = v.coerceIn(30, 600)) }
    fun updateTxRepeats(v: Int)    { eslTarget = eslTarget.copy(txRepeats= v) }
    fun updateDelay(v: Int)        { eslTarget = eslTarget.copy(delayMs  = v) }

    fun recalcPlid() {
        val plid  = EslProtocol.barcodeToPlid(eslTarget.barcode)
        plidHex   = EslProtocol.plidHex(plid)
        crcHex    = EslProtocol.crcHex(plid, EslProtocol.CMD_TEXT)
    }

    // ── IR Config ───────────────────────────────────────────────────────────
    var irConfig by mutableStateOf(IrConfig())
        private set

    var generatedIr by mutableStateOf("")
        private set

    var generatedIrCount by mutableStateOf(0)
        private set

    var isGeneratingIr by mutableStateOf(false)
        private set

    fun updateIrCommand(v: Int)     { irConfig = irConfig.copy(commandType = v) }
    fun updateIrText(v: String)     { irConfig = irConfig.copy(textContent = v) }
    fun updateIrInvert(v: Boolean)  { irConfig = irConfig.copy(invertText  = v) }
    fun updateColorClear(v: Boolean){ irConfig = irConfig.copy(colorClear  = v) }
    fun updateLedMode(v: Int)       { irConfig = irConfig.copy(ledMode     = v) }

    fun generateIrFile() {
        val command = irConfig.commandType
        val text = irConfig.textContent
        val invert = irConfig.invertText
        val ledMode = irConfig.ledMode
        val colorClear = irConfig.colorClear
        val barcode = eslTarget.barcode

        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) { 
                isGeneratingIr = true 
                generatedIr = "" // Clear previous output
            }
            val payload = when (command) {
                EslProtocol.CMD_TEXT  -> EslProtocol.textPayload(text, invert)
                EslProtocol.CMD_TEST  -> EslProtocol.testPayload()
                EslProtocol.CMD_LED   -> EslProtocol.ledPayload(ledMode)
                EslProtocol.CMD_CLEAR -> EslProtocol.clearPayload(colorClear)
                else -> byteArrayOf()
            }
            val ir = EslProtocol.buildIrFile(barcode, command, payload)
            val count = 3 + (7 + 1 + payload.size + 1) * 16
            withContext(Dispatchers.Main) {
                generatedIr = ir
                generatedIrCount = count
                isGeneratingIr = false
            }
        }
    }

    fun saveIrToFile(context: Context): File? {
        if (generatedIr.isBlank()) return null
        return try {
            val dir  = context.getExternalFilesDir(null) ?: context.cacheDir
            val file = File(dir, "tagtinker_esl.ir")
            file.writeText(generatedIr)
            file
        } catch (e: Exception) { null }
    }

    // ── Image Config ─────────────────────────────────────────────────────────
    var imageConfig by mutableStateOf(ImageConfig())
        private set

    var sourceBitmap    by mutableStateOf<Bitmap?>(null)
        private set
    var processedImage  by mutableStateOf<ImageProcessor.ProcessedImage?>(null)
        private set
    var isProcessing    by mutableStateOf(false)
        private set
    var imageStatus     by mutableStateOf<Pair<Boolean, String>?>(null) // ok, message
        private set

    var xbmOutput   by mutableStateOf("")
        private set
    var jsSnippet   by mutableStateOf("")
        private set

    fun updateThreshold(v: Int)       { imageConfig = imageConfig.copy(threshold  = v); reprocess() }
    fun updateDitherMode(v: String)   { imageConfig = imageConfig.copy(ditherMode = v); reprocess() }
    fun updateInvert(v: Boolean)      { imageConfig = imageConfig.copy(invert     = v); reprocess() }
    fun updateFitToEsl(v: Boolean)    { imageConfig = imageConfig.copy(fitToEsl   = v); reprocess() }

    fun loadImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                withContext(Dispatchers.Main) {
                    sourceBitmap = bmp
                    imageStatus  = Pair(true, "✓ Loaded: ${bmp.width}×${bmp.height} px")
                    reprocess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    imageStatus = Pair(false, "✗ Failed to load image")
                }
            }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        updateBarcode(barcode)
    }

    private fun reprocess() {
        val bmp = sourceBitmap ?: return
        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) { isProcessing = true }
            val result = ImageProcessor.processImage(
                source     = bmp,
                targetW    = eslTarget.widthPx,
                targetH    = eslTarget.heightPx,
                threshold  = imageConfig.threshold,
                ditherMode = imageConfig.ditherMode,
                invert     = imageConfig.invert,
                fitToEsl   = imageConfig.fitToEsl,
            )
            withContext(Dispatchers.Main) {
                processedImage = result
                isProcessing   = false
                xbmOutput = ""
                jsSnippet = ""
            }
        }
    }

    fun exportXbm() {
        val proc = processedImage ?: return
        val xbm  = ImageProcessor.exportXbm(proc, imageConfig.invert)
        xbmOutput = xbm
        jsSnippet = ImageProcessor.buildJsSnippet(proc, xbm)
    }

    fun clearImage() {
        sourceBitmap   = null
        processedImage = null
        imageStatus    = null
        xbmOutput = ""; jsSnippet = ""
    }

    fun buildIrFromImage() {
        val proc = processedImage ?: return
        val barcode = eslTarget.barcode
        val invert = imageConfig.invert
        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) { 
                isGeneratingIr = true 
                generatedIr = "" // Clear previous output
            }
            val payload = ImageProcessor.imageToIrPayload(proc, invert)
            val ir = EslProtocol.buildIrFile(barcode, EslProtocol.CMD_IMAGE, payload)
            val count = 3 + (7 + 1 + payload.size + 1) * 16
            withContext(Dispatchers.Main) {
                generatedIr = ir
                generatedIrCount = count
                isGeneratingIr = false
            }
        }
    }

    // ── MULTIPASS JS script content ──────────────────────────────────────────
    val multipassScript = """
// ESL Tag Tools // by mostlyAWESOME Media
// ESL (Electronic Shelf Label) IR research tool
// Based on TagTinker by i12bp8 — Protocol research by furrtek
// GPL-3.0 License — ONLY use on hardware you own or are authorized to test.

var ir = require("ir");
var display = require("display");
var dialog = require("dialog");
var keyboard = require("keyboard");
var storage = require("storage");

var ESL_CMD_TEXT  = 0x10;
var ESL_CMD_IMAGE = 0x20;
var ESL_CMD_TEST  = 0x30;
var ESL_CMD_LED   = 0x40;
var ESL_CMD_CLEAR = 0x50;

var C_BG    = display.color(10,  10,  18);
var C_PRI   = display.color(0,   210, 120);
var C_ACC   = display.color(255, 200, 0);
var C_TXT   = display.color(220, 220, 230);
var C_DIM   = display.color(80,  80,  100);
var C_RED   = display.color(200, 50,  50);
var C_PANEL = display.color(22,  22,  36);

var W = display.width();
var H = display.height();

var state = {
  barcode: "N4611470825513274",
  page: 0,
  duration: 15,
  repeats: 200,
  eslW: 200,
  eslH: 80,
  textContent: "TagTinker",
  invertText: false,
  colorClear: false,
  ledMode: 0
};

function barcodeToPlid(barcode) {
  var numeric = barcode.replace(/[^0-9]/g, "");
  var digits = numeric.slice(-13);
  while (digits.length < 13) digits = "0" + digits;
  var plid = [];
  for (var i = 0; i < 7; i++) {
    var hi = parse_int(digits[i * 2] || "0");
    var lo = parse_int(digits[i * 2 + 1] || "0");
    plid.push((hi << 4) | lo);
  }
  return plid;
}

function transmitEsl(command, payload) {
  var plid = barcodeToPlid(state.barcode);
  var packet = plid.slice();
  packet.push(command);
  if (payload) for (var i = 0; i < payload.length; i++) packet.push(payload[i]);
  var crc = 0;
  for (var i = 0; i < packet.length; i++) crc ^= packet[i];
  packet.push(crc & 0xFF);
  var timings = [9000, 4500];
  for (var b = 0; b < packet.length; b++) {
    var bv = packet[b];
    for (var bit = 0; bit < 8; bit++) {
      if (bv & (1 << bit)) { timings.push(560); timings.push(1690); }
      else { timings.push(560); timings.push(560); }
    }
  }
  timings.push(560);
  for (var r = 0; r < state.repeats; r++) {
    ir.transmitRaw(timings, 1255000, 0.33);
    delay(state.duration);
  }
}

dialog.message("ESL Tag Tools", "ESL Controller\nReady");
transmitEsl(ESL_CMD_TEXT, [0, 9, 84, 97, 103, 84, 105, 110, 107, 101, 114]);
""".trimIndent()

    fun saveMultipassScript(context: Context): File? {
        return try {
            val dir  = context.getExternalFilesDir(null) ?: context.cacheDir
            val file = File(dir, "esl_tools.js")
            file.writeText(multipassScript)
            file
        } catch (e: Exception) { null }
    }
}
