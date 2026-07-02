package com.example.skinhistoryscanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.skinhistoryscanner.R

@Composable
fun ImportActionDialog(
    onOverwrite: () -> Unit,
    onNewProfile: (String) -> Unit,
    onCancel: () -> Unit
) {
    var newProfileName by remember { mutableStateOf("") }
    var showNameField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.import_database_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_database_desc))
                if (showNameField) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text(stringResource(R.string.import_new_profile_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Column {
                if (!showNameField) {
                    Button(onClick = onOverwrite, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_update_current)) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showNameField = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_create_new)) }
                } else {
                    Button(
                        onClick = { onNewProfile(newProfileName) },
                        enabled = newProfileName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.import_confirm_name)) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } }
    )
}
