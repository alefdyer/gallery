package com.asinosoft.gallery.di

import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.local.LocalMediaService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
abstract class ServiceModule {
    @Binds
    abstract fun providesMediaService(localMediaService: LocalMediaService): MediaService
}
