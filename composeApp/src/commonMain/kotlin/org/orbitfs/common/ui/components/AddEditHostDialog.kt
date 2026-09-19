package org.orbitfs.common.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Random

@Composable
fun AddEditHostDialog(
    showDialog: Boolean,
    initialName: String = "",
    initialHost: String = "",
    initialPort: String = "9090",
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, host: String, port: Int) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    if (showDialog) {
        val isEdit = initialName.isNotEmpty()
        val defaultName = remember { "Satellite ${Random().nextInt(100) + 1}" }
        var nameText by rememberSaveable { mutableStateOf(initialName.ifEmpty { defaultName }) }
        var hostText by rememberSaveable { mutableStateOf(initialHost) }
        var portText by rememberSaveable { mutableStateOf(initialPort) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(if (isEdit) "Edit Node" else "Add New Node") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Node Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = { hostText = it },
                        label = { Text("Host (IP or hostname)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            if (it.isEmpty() || it.all { c -> c.isDigit() }) portText = it
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    if (isEdit && onDelete != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Remove this Node")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (nameText.isNotBlank() && hostText.isNotBlank() && port != null) {
                            onConfirm(nameText, hostText, port)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEdit) "Save" else "Add Node")
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
