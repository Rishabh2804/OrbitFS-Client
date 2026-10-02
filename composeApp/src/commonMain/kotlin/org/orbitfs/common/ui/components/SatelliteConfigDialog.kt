package org.orbitfs.common.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteConfigDialog(
    initialPort: Int,
    initialPath: String?,
    onDismiss: () -> Unit,
    onConfirm: (Int, String?) -> Unit,
    onPickFolder: () -> Unit
) {
    var port by remember { mutableStateOf(initialPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Satellite Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Broadcast Port") },
                    leadingIcon = { Icon(Icons.Rounded.Router, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = initialPath ?: "Internal Storage (Default)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Shared Directory") },
                    leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = onPickFolder) {
                            Text("Change")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(port.toIntOrNull() ?: 9090, initialPath) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm & Restart")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
