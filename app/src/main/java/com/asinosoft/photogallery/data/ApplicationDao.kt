package com.asinosoft.photogallery.data

interface ApplicationDao {
    suspend fun getApplications(packages: Set<String>): List<Application>
}
