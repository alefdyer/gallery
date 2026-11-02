package com.asinosoft.photogallery.ui.people

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.people.Face
import com.asinosoft.photogallery.data.people.Person
import com.asinosoft.photogallery.data.people.RelationshipKey
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonEditScreen(
    onClose: () -> Unit,
    onPickRelationship: () -> Unit,
    onMerged: () -> Unit = onClose,
    onSplitCreated: (Long) -> Unit = {},
    model: PersonEditViewModel = hiltViewModel(),
) {
    val person by model.personState.collectAsState()
    val faces by model.faces.collectAsState()
    val named by model.namedPeople.collectAsState()
    val allPeople by model.allPeople.collectAsState()
    val name by model.name.collectAsState()
    val relationship by model.relationship.collectAsState()
    val contactUri by model.contactUri.collectAsState()
    val context = LocalContext.current
    val canSave = name.trim().isNotEmpty() || contactUri != null

    var showCoverPicker by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }
    var showSplitPicker by remember { mutableStateOf(false) }
    var permissionHint by remember { mutableStateOf(false) }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val display = it.getString(1)
                model.setContact(uri.toString(), id, display)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionHint = false
            contactPicker.launch(null)
        } else {
            permissionHint = true
        }
    }

    fun linkContact() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            contactPicker.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    if (showCoverPicker) {
        FaceCoverPicker(
            faces = faces,
            selectedFaceId = person?.coverFaceId,
            onSelect = {
                model.setCover(it)
                showCoverPicker = false
            },
            onClose = { showCoverPicker = false }
        )
        return
    }

    if (showMergePicker) {
        PeoplePickerDialog(
            title = stringResource(R.string.people_select_merge_target),
            people = allPeople,
            onPick = { target ->
                showMergePicker = false
                model.mergeWith(target.id, onMerged)
            },
            onClose = { showMergePicker = false }
        )
        return
    }

    if (showSplitPicker) {
        FaceSplitPicker(
            faces = faces,
            onConfirm = { ids ->
                showSplitPicker = false
                model.splitFaces(ids) { newId ->
                    if (newId != null) onSplitCreated(newId)
                }
            },
            onClose = { showSplitPicker = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_add_name)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { model.save(onClose) },
                        enabled = canSave
                    ) {
                        Text(stringResource(R.string.save))
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val coverFace = faces.firstOrNull { it.id == person?.coverFaceId }
                ?: faces.firstOrNull()
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                FaceThumb(
                    face = coverFace,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
                IconButton(
                    onClick = { showCoverPicker = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        painterResource(R.drawable.edit),
                        contentDescription = stringResource(R.string.people_pick_cover),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = model::setName,
                label = { Text(stringResource(R.string.people_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            val relLabel = relationship?.let { stored ->
                RelationshipKey.labelResOrNull(stored)?.let { stringResource(it) } ?: stored
            }.orEmpty()
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickRelationship)
            ) {
                OutlinedTextField(
                    value = relLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.people_relationship)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(painterResource(R.drawable.chevron_right), contentDescription = null)
                    }
                )
            }

            Text(
                text = stringResource(R.string.people_suggested_names),
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    label = stringResource(R.string.people_rel_me),
                    onClick = {
                        model.setName(context.getString(R.string.people_rel_me))
                        model.setRelationship(RelationshipKey.ME.key)
                    }
                )
                named.forEach { p ->
                    SuggestionChip(
                        label = p.displayName.orEmpty(),
                        onClick = { model.setName(p.displayName.orEmpty()) }
                    )
                }
            }

            TextButton(onClick = ::linkContact) {
                Text(stringResource(R.string.people_link_contact))
            }
            if (permissionHint) {
                Text(
                    stringResource(R.string.people_contacts_permission),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showMergePicker = true }) {
                Text(stringResource(R.string.people_merge))
            }
            TextButton(onClick = { showSplitPicker = true }) {
                Text(stringResource(R.string.people_split))
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun FaceThumb(face: Face?, modifier: Modifier = Modifier) {
    val path = face?.thumbPath
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceCoverPicker(
    faces: List<Face>,
    selectedFaceId: Long?,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_pick_cover)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(faces, key = { it.id }) { face ->
                val selected = face.id == selectedFaceId
                FaceThumb(
                    face = face,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (selected) {
                                Modifier.border(
                                    3.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(16.dp)
                                )
                            } else Modifier
                        )
                        .clickable { onSelect(face.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaceSplitPicker(
    faces: List<Face>,
    onConfirm: (Set<Long>) -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_split)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onConfirm(selected) },
                        enabled = selected.isNotEmpty() && selected.size < faces.size
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                stringResource(R.string.people_split_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(faces, key = { it.id }) { face ->
                    val on = face.id in selected
                    FaceThumb(
                        face = face,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                if (on) 3.dp else 0.dp,
                                if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selected = if (on) selected - face.id else selected + face.id
                            }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeoplePickerDialog(
    title: String,
    people: List<Person>,
    onPick: (Person) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            people.forEach { person ->
                val label = person.displayName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.people_unnamed)
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPick(person) }
                        .padding(16.dp)
                )
            }
        }
    }
}
