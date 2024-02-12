package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun PermissionDisclaimer(requestPermissions: () -> Unit) {
    Card(modifier = Modifier.padding(16.dp)) {
        Text(text = "Ошэнь нана!")
        Button(onClick = requestPermissions) {
            Text(text = "Добро")
        }
    }
}

@Preview
@Composable
fun Preview() {
    PermissionDisclaimer { }
}
