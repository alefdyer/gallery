package com.asinosoft.photogallery.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.data.people.Face
import com.asinosoft.photogallery.data.people.PeopleRepository
import com.asinosoft.photogallery.data.people.Person
import com.asinosoft.photogallery.data.people.PersonDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PersonEditViewModel @Inject constructor(
    state: SavedStateHandle,
    private val people: PeopleRepository,
    personDao: PersonDao,
) : ViewModel() {
    val personId: Long = checkNotNull(state["personId"])

    val personState: StateFlow<Person?> = personDao.observePerson(personId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _faces = MutableStateFlow<List<Face>>(emptyList())
    val faces: StateFlow<List<Face>> = _faces

    val namedPeople: StateFlow<List<Person>> = people.observePeople()
        .map { list ->
            list.filter { !it.displayName.isNullOrBlank() && it.id != personId }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPeople: StateFlow<List<Person>> = people.observePeople()
        .map { list -> list.filter { it.id != personId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val name = MutableStateFlow("")
    val relationship = MutableStateFlow<String?>(null)
    val contactUri = MutableStateFlow<String?>(null)
    val contactId = MutableStateFlow<Long?>(null)

    private var hydrated = false

    init {
        viewModelScope.launch {
            personState.collect { p ->
                if (p != null && !hydrated) {
                    hydrated = true
                    name.value = p.displayName.orEmpty()
                    relationship.value = p.relationship
                    contactUri.value = p.contactUri
                    contactId.value = p.contactId
                }
                _faces.value = people.getFacesForPerson(personId)
            }
        }
    }

    fun setName(value: String) {
        name.value = value
    }

    fun setRelationship(value: String?) {
        relationship.value = value
    }

    fun setContact(uri: String?, id: Long?, displayName: String?) {
        contactUri.value = uri
        contactId.value = id
        if (!displayName.isNullOrBlank() && name.value.isBlank()) {
            name.value = displayName
        }
    }

    fun canSave(): Boolean =
        name.value.trim().isNotEmpty() || contactUri.value != null

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (!canSave()) return@launch
        people.updatePerson(
            personId = personId,
            displayName = name.value,
            relationship = relationship.value,
            contactUri = contactUri.value,
            contactId = contactId.value,
        )
        onDone()
    }

    fun setCover(faceId: Long) = viewModelScope.launch {
        people.setCoverFace(personId, faceId)
        _faces.value = people.getFacesForPerson(personId)
    }

    fun mergeWith(targetPersonId: Long, onDone: () -> Unit) = viewModelScope.launch {
        people.mergePeople(sourcePersonId = personId, targetPersonId = targetPersonId)
        onDone()
    }

    fun splitFaces(faceIds: Collection<Long>, onDone: (Long?) -> Unit) = viewModelScope.launch {
        val newId = people.splitFaces(personId, faceIds)
        onDone(newId)
    }
}
