package com.asinosoft.photogallery.di

import android.content.Context
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.AppDatabase
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.storage.StorageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object DatabaseModule {
    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    fun provideAlbumDao(appDatabase: AppDatabase): AlbumDao = appDatabase.albumDao()

    @Provides
    fun provideImageDao(appDatabase: AppDatabase): MediaDao = appDatabase.imageDao()

    @Provides
    fun provideStorageDao(appDatabase: AppDatabase): StorageDao = appDatabase.storageDao()
}
