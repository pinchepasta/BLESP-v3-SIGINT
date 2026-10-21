package com.bruceremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bruceremote.app.serial.ActionCategory
import com.bruceremote.app.serial.QuickAction
import com.bruceremote.app.ui.theme.BruceBorder
import com.bruceremote.app.ui.theme.BruceErr
import com.bruceremote.app.ui.theme.BruceMuted
import com.bruceremote.app.ui.theme.BruceSurface
import com.bruceremote.app.ui.theme.BruceSurfaceAlt
import com.bruceremote.app.ui.theme.NodeAccent
import com.bruceremote.app.ui.theme.NodeBg
import com.bruceremote.app.ui.theme.NodePrimary

@Composable
fun QuickActionsScreen(
    actions: List<QuickAction>,
    connected: Boolean,
    onSend: (String) -> Unit,
    onAddAction: (label: String, command: String, category: ActionCategory) -> Unit,
    onEditAction: (id: String, label: String, command: String, category: ActionCategory) -> Unit,
    onDeleteAction: (id: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(ActionCategory.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var actionToEdit by remember { mutableStateOf<QuickAction?>(null) }

    val filteredActions = remember(actions, selectedCategory) {
        if (selectedCategory == ActionCategory.ALL) actions
        else actions.filter { it.category == selectedCategory }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Top Filter Bar & Add Button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ActionCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                cat.displayName,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("+ Shortcut", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        if (filteredActions.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No shortcuts found for ${selectedCategory.displayName}.\nTap '+ Shortcut' to create one!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredActions, key = { it.id }) { action ->
                    QuickActionCard(
                        action = action,
                        enabled = connected,
                        onClick = { onSend(action.command) },
                        onEdit = { actionToEdit = action }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ActionEditorDialog(
            initialCategory = if (selectedCategory != ActionCategory.ALL) selectedCategory else ActionCategory.CUSTOM,
            onDismiss = { showAddDialog = false },
            onSave = { label, command, cat ->
                onAddAction(label, command, cat)
                showAddDialog = false
            }
        )
    }

    actionToEdit?.let { action ->
        ActionEditorDialog(
            action = action,
            onDismiss = { actionToEdit = null },
            onSave = { label, command, cat ->
                onEditAction(action.id, label, command, cat)
                actionToEdit = null
            },
            onDelete = if (action.isCustom) {
                {
                    onDeleteAction(action.id)
                    actionToEdit = null
                }
            } else null
        )
    }
}

@Composable
private fun QuickActionCard(
    action: QuickAction,
    enabled: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val categoryColor = when (action.category) {
        ActionCategory.WIFI -> MaterialTheme.colorScheme.secondary
        ActionCategory.BLE -> MaterialTheme.colorScheme.primary
        ActionCategory.SYSTEM -> Color(0xFFFFB300)
        ActionCategory.CUSTOM -> MaterialTheme.colorScheme.tertiary
        ActionCategory.ALL -> MaterialTheme.colorScheme.primary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = action.category.displayName,
                    color = categoryColor,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Button(
                enabled = enabled,
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = action.label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = action.command,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionEditorDialog(
    action: QuickAction? = null,
    initialCategory: ActionCategory = ActionCategory.CUSTOM,
    onDismiss: () -> Unit,
    onSave: (label: String, command: String, category: ActionCategory) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var label by remember { mutableStateOf(action?.label ?: "") }
    var command by remember { mutableStateOf(action?.command ?: "") }
    var category by remember { mutableStateOf(action?.category ?: initialCategory) }

    val categories = remember {
        listOf(ActionCategory.WIFI, ActionCategory.BLE, ActionCategory.SYSTEM, ActionCategory.CUSTOM)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (action == null) "Add Custom Shortcut" else "Edit Shortcut",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label", fontSize = 11.sp) },
                    placeholder = { Text("e.g. WiFi Scan or BLE Spam", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NodePrimary,
                        unfocusedBorderColor = BruceBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command", fontSize = 11.sp) },
                    placeholder = { Text("e.g. wifi scan or ble spam", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NodePrimary,
                        unfocusedBorderColor = BruceBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category:", fontSize = 11.sp, color = BruceMuted)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NodePrimary,
                                selectedLabelColor = NodeBg,
                                containerColor = BruceSurface,
                                labelColor = NodePrimary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = label.isNotBlank() && command.isNotBlank(),
                onClick = { onSave(label, command, category) },
                colors = ButtonDefaults.buttonColors(containerColor = NodePrimary, contentColor = NodeBg)
            ) {
                Text("Save", fontSize = 11.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BruceErr)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 11.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 11.sp)
                }
            }
        }
    )
}
