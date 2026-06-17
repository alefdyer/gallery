package com.asinosoft.gallery.di

import com.asinosoft.gallery.data.ApplicationDao
import com.asinosoft.gallery.data.ApplicationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun providesApplicationDao(repository: ApplicationRepository): ApplicationDao
}
