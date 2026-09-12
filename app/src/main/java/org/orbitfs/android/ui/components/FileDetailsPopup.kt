package org.orbitfs.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.orbitfs.android.model.FileInfo
import org.orbitfs.android.ui.theme.*
import org.orbitfs.android.util.MimeTypeUtil

@Composable
fun FileDetailsPopup(
    fileInfo: FileInfo?,
    onClose: () -> Unit
) {
    if (fileInfo == null) return
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Information", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(fileInfo.displayName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    if (fileInfo.isDirectory) "Directory" else "${MimeTypeUtil.formatFileSize(fileInfo.size)} (${fileInfo.size} bytes)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                MetadataGroup(title = "System Details") {
                    MetadataRow(label = "Type", value = fileInfo.getFileType())
                    MetadataRow(label = "Modified", value = MimeTypeUtil.formatDate(fileInfo.lastModified))
                    MetadataRow(label = "Permissions", value = fileInfo.permissions.ifEmpty { "Default (0644)" })
                    MetadataRow(label = "Owner", value = fileInfo.owner.ifEmpty { "orbitfs" })
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Server Path", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fileInfo.path,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("OrbitFS Path", fileInfo.path)
                                clipboard.setPrimaryClip(clip)
                                copied = true
                                Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (copied) ColorStatusGreen else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun MetadataGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Column(content = content)
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
fun FileDetailsPreview() {
    OrbitFSTheme {
        FileDetailsPopup(
            fileInfo = FileInfo(
                name = "project_manifest.json",
                path = "/home/user/vault/project_manifest.json",
                size = 1024,
                isDirectory = false,
                lastModified = 1726116300000L
            ),
            onClose = {}
        )
    }
}
