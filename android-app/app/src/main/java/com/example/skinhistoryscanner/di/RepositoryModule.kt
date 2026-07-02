package com.example.skinhistoryscanner.di

import com.example.skinhistoryscanner.data.repository.MoleRepository
import com.example.skinhistoryscanner.data.repository.OfflineMoleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMoleRepository(
        offlineMoleRepository: OfflineMoleRepository
    ): MoleRepository
}
