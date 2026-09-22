package com.mostlyawesome.tagtinker

import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mostlyawesome.tagtinker.ui.screens.BarcodeScannerScreen
import com.mostlyawesome.tagtinker.ui.screens.MainScreen
import com.mostlyawesome.tagtinker.ui.theme.TagTinkerTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val darkTheme = vm.themeSelection ?: isSystemInDarkTheme()
            TagTinkerTheme(darkTheme = darkTheme) {
                AppContent(vm)
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun AppContent(vm: MainViewModel) {
        val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
        var showScanner by remember { mutableStateOf(false) }

        if (showScanner) {
            BarcodeScannerScreen(
                onBarcodeScanned = { barcode ->
                    vm.onBarcodeScanned(barcode)
                    showScanner = false
                },
                onDismiss = { showScanner = false }
            )
        } else {
            MainScreen(
                vm = vm,
                onOpenScanner = {
                    if (cameraPermission.status.isGranted) {
                        showScanner = true
                    } else {
                        cameraPermission.launchPermissionRequest()
                    }
                }
            )
        }
    }
}
