package com.example.skinhistoryscanner.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MoleEntity::class, 
        HistoryEntryEntity::class,
        BackgroundCategoryEntity::class,
        BackgroundVariantEntity::class,
        Mole3DEntity::class,
        HistoryEntry3DEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabaseRoom : RoomDatabase() {
    abstract fun moleDao(): MoleDao
    abstract fun backgroundDao(): BackgroundDao
    abstract fun mole3DDao(): Mole3DDao
}
