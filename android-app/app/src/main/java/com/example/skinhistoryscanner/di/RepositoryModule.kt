package com.example.skinhistoryscanner.di

import com.example.skinhistoryscanner.data.repository.MoleRepository
import com.example.skinhistoryscanner.data.repository.OfflineMoleRepository
import com.example.skinhistoryscanner.data.repository.Mole3DRepository
import com.example.skinhistoryscanner.data.repository.OfflineMole3DRepository
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

    @Binds
    @Singleton
    abstract fun bindMole3DRepository(
        offlineMole3DRepository: OfflineMole3DRepository
    ): Mole3DRepository
}
