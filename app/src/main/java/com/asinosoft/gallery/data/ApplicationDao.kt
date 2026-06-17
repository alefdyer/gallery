package com.asinosoft.gallery.data

interface ApplicationDao {
    suspend fun getApplications(): List<Application>
}
