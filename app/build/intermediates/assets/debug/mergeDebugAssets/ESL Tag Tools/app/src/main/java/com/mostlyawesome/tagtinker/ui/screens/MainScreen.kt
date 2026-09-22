package com.mostlyawesome.tagtinker.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.mostlyawesome.tagtinker.MainViewModel
import com.mostlyawesome.tagtinker.R
import com.mostlyawesome.tagtinker.ui.components.*
import com.mostlyawesome.tagtinker.ui.theme.Orange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    onOpenScanner: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf("Image", "IR File", "XBM Export", "Protocol")

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 0.dp,
                color          = MaterialTheme.colorScheme.surface,
                modifier       = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Brand area — Logo from URL
                        AsyncImage(
                            model = "https://mostlyawesome.de/dev/brandnewmostlyawesomelogo.png",
                            contentDescription = "mostlyAWESOME Logo",
                            modifier = Modifier
                                .height(32.dp)
                                .widthIn(max = 160.dp),
                            contentScale = ContentScale.Fit
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isSystemDark = isSystemInDarkTheme()

                            // Theme toggle
                            FilledTonalIconButton(
                                onClick = {
                                    // Cycle: Auto -> Dark -> Light -> Auto
                                    vm.themeSelection = when (vm.themeSelection) {
                                        null -> true
                                        true -> false
                                        false -> null
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    when (vm.themeSelection) {
                                        null -> Icons.Default.BrightnessAuto
                                        true -> Icons.Default.DarkMode
                                        false -> Icons.Default.LightMode
                                    },
                                    "Toggle theme",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Page title
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    "ESL Tag Scanner, Image Converter and IR Payload Generator",
                    fontSize   = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    color      = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Generate IR payloads for Electronic Shelf Labels, convert images to XBM format, " +
                    "and deploy scripts to MULTIPASS firmware. Research use only — only operate on hardware you own.",
                    fontSize   = 13.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Light,
                )
            }

            // Left column cards
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TargetEslSection(vm = vm, onOpenScanner = onOpenScanner)
                DeploySection(vm = vm)

                // Right column — tabbed card
                TtCard {
                    // Tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor   = MaterialTheme.colorScheme.surface,
                        contentColor     = MaterialTheme.colorScheme.onSurface,
                        edgePadding      = 0.dp,
                        indicator        = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color    = MaterialTheme.colorScheme.onSurface,
                                height   = 2.dp,
                            )
                        },
                        divider = { Divider(color = MaterialTheme.colorScheme.outline) }
                    ) {
                        tabs.forEachIndexed { i, label ->
                            Tab(
                                selected = selectedTab == i,
                                onClick  = { selectedTab = i },
                                text     = {
                                    Text(
                                        label,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize   = 10.sp,
                                        letterSpacing = 0.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                },
                                selectedContentColor   = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }

                    // Tab content
                    when (selectedTab) {
                        0 -> ImageTab(vm = vm, onGoToIr = { selectedTab = 1 })
                        1 -> IrFileTab(vm = vm)
                        2 -> XbmTab(vm = vm)
                        3 -> ProtocolTab(vm = vm)
                    }
                }

                // Footer attribution
                Text(
                    "Made by: mostlyAWESOME Media",
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    lineHeight = 14.sp,
                    modifier   = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// Extension for tab indicator offset (manual since tabIndicatorOffset requires import)
private fun Modifier.tabIndicatorOffset(tabPosition: androidx.compose.material3.TabPosition): Modifier =
    this.then(
        Modifier.wrapContentSize(Alignment.BottomStart)
            .offset(x = tabPosition.left)
            .width(tabPosition.width)
    )
