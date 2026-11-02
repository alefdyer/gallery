package com.asinosoft.photogallery.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.people.RelationshipKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipPickerScreen(
    current: String?,
    onPick: (String?) -> Unit,
    onClose: () -> Unit,
) {
    var customMode by remember {
        mutableStateOf(
            current != null && RelationshipKey.fromStored(current) == null
        )
    }
    var customText by remember {
        mutableStateOf(
            if (customMode) current.orEmpty() else ""
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_relationship)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (customMode) {
                        TextButton(
                            onClick = {
                                val t = customText.trim()
                                if (t.isNotEmpty()) onPick(t)
                            },
                            enabled = customText.trim().isNotEmpty()
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            RelationshipKey.entries
                .filter { it != RelationshipKey.OTHER }
                .forEach { key ->
                    Text(
                        text = stringResource(key.labelRes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(key.key) }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            Text(
                text = stringResource(R.string.people_rel_other),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { customMode = true }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
            if (customMode) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text(stringResource(R.string.people_rel_custom_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}
