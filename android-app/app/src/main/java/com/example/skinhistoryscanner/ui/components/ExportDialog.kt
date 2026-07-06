package com.example.skinhistoryscanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.skinhistoryscanner.data.domain.PdfQuality

@Composable
fun ExportDialog(
    initialQuality: PdfQuality,
    initialOpenPdf: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PdfQuality, Boolean, Boolean) -> Unit
) {
    var quality by remember { mutableStateOf(initialQuality) }
    var openPdf by remember { mutableStateOf(initialOpenPdf) }
    var dontShowAgain by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opzioni di esportazione PDF", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                Column {
                    Text("Qualità Immagini", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showQualityMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(when(quality) {
                            PdfQuality.LOW -> "Bassa (Veloce, Leggera)"
                            PdfQuality.MEDIUM -> "Media (Bilanciata)"
                            PdfQuality.HIGH -> "Alta (FullHD, Pesante)"
                        })
                    }
                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false }
                    ) {
                        PdfQuality.entries.forEach { q ->
                            DropdownMenuItem(
                                text = { Text(when(q) {
                                    PdfQuality.LOW -> "Bassa (Veloce, Leggera)"
                                    PdfQuality.MEDIUM -> "Media (Bilanciata)"
                                    PdfQuality.HIGH -> "Alta (FullHD, Pesante)"
                                }) },
                                onClick = {
                                    quality = q
                                    showQualityMenu = false
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { openPdf = !openPdf }
                ) {
                    Checkbox(checked = openPdf, onCheckedChange = { openPdf = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Apri automaticamente al termine", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { dontShowAgain = !dontShowAgain }
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Non mostrare più questo messaggio", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(quality, openPdf, !dontShowAgain) }) {
                Text("Esporta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
