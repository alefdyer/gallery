package com.asinosoft.gallery.data

interface ApplicationDao {
    suspend fun getApplications(packages: Set<String>): List<Application>
}
