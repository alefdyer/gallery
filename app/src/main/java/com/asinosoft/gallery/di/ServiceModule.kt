package com.asinosoft.gallery.di

import com.asinosoft.gallery.data.LocalMediaService
import com.asinosoft.gallery.data.MediaService
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
