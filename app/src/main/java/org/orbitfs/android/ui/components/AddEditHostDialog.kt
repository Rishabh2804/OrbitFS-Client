package org.orbitfs.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun AddEditHostDialog(
    showDialog: Boolean,
    initialName: String = "",
    initialHost: String = "",
    initialPort: String = "9090",
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, host: String, port: Int) -> Unit
) {
    if (showDialog) {
        var nameText by rememberSaveable { mutableStateOf(initialName) }
        var hostText by rememberSaveable { mutableStateOf(initialHost) }
        var portText by rememberSaveable { mutableStateOf(initialPort) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(if (initialName.isNotEmpty()) "Edit Host" else "Add Host") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = { hostText = it },
                        label = { Text("Host (IP or hostname)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            if (it.isEmpty() || it.all { c -> c.isDigit() }) portText = it
                        },
                        label = { Text("Port") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (hostText.isNotBlank() && port != null) {
                            onConfirm(nameText.ifEmpty { hostText }, hostText, port)
                        }
                    }
                ) {
                    Text(if (initialName.isNotEmpty()) "Save" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        )
    }
}
