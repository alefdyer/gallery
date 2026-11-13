package com.asinosoft.photogallery.di

import com.asinosoft.photogallery.data.MediaDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaDaoEntryPoint {
    fun mediaDao(): MediaDao
}
