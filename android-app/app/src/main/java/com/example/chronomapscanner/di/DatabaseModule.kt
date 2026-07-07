package com.example.chronomapscanner.di

import android.content.Context
import androidx.room.Room
import com.example.chronomapscanner.data.local.room.AppDatabaseRoom
import com.example.chronomapscanner.data.local.room.MoleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_moles_profile_side_date` ON `moles` (`profileName`, `side`, `created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_moles_color` ON `moles` (`color`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_history_date` ON `history_entries` (`date`)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `moles_new` (
                    `id` TEXT NOT NULL, 
                    `profileName` TEXT NOT NULL, 
                    `x` REAL NOT NULL, 
                    `y` REAL NOT NULL, 
                    `side` TEXT NOT NULL, 
                    `color` TEXT NOT NULL, 
                    PRIMARY KEY(`id`)
                )
            """)
            db.execSQL("""
                INSERT INTO `moles_new` (`id`, `profileName`, `x`, `y`, `side`, `color`)
                SELECT `id`, `profileName`, `x`, `y`, `side`, `color` FROM `moles`
            """)
            db.execSQL("DROP TABLE `moles`")
            db.execSQL("ALTER TABLE `moles_new` RENAME TO `moles`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_moles_profile_side` ON `moles` (`profileName`, `side`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_moles_color` ON `moles` (`color`)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `moles_new` (
                    `id` TEXT NOT NULL, 
                    `profileName` TEXT NOT NULL, 
                    `x` REAL NOT NULL, 
                    `y` REAL NOT NULL, 
                    `variantId` TEXT NOT NULL, 
                    `color` TEXT NOT NULL, 
                    PRIMARY KEY(`id`)
                )
            """)
            db.execSQL("""
                INSERT INTO `moles_new` (`id`, `profileName`, `x`, `y`, `variantId`, `color`)
                SELECT `id`, `profileName`, `x`, `y`, `side`, `color` FROM `moles`
            """)
            db.execSQL("DROP TABLE `moles`")
            db.execSQL("ALTER TABLE `moles_new` RENAME TO `moles`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_moles_profile_variant` ON `moles` (`profileName`, `variantId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_moles_color` ON `moles` (`color`)")
            
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `background_categories` (
                    `id` TEXT NOT NULL, 
                    `profileName` TEXT NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `isBuiltIn` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_bg_categories_profile` ON `background_categories` (`profileName`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `background_variants` (
                    `id` TEXT NOT NULL, 
                    `categoryId` TEXT NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `imagePath` TEXT, 
                    `orderIndex` INTEGER NOT NULL, 
                    `dateAdded` TEXT NOT NULL, 
                    `notes` TEXT, 
                    PRIMARY KEY(`id`), 
                    FOREIGN KEY(`categoryId`) REFERENCES `background_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_bg_variants_category` ON `background_variants` (`categoryId`)")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Migrate history_entries date from TEXT (ISO8601) to INTEGER (Epoch Days)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `history_entries_new` (
                    `id` TEXT NOT NULL, 
                    `mole_id` TEXT NOT NULL, 
                    `date` INTEGER NOT NULL, 
                    `imagePath` TEXT, 
                    `notes` TEXT, 
                    PRIMARY KEY(`id`), 
                    FOREIGN KEY(`mole_id`) REFERENCES `moles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                )
            """)
            db.execSQL("""
                INSERT INTO `history_entries_new` (`id`, `mole_id`, `date`, `imagePath`, `notes`)
                SELECT `id`, `mole_id`, CAST((julianday(`date`) - 2440587.5) AS INTEGER), `imagePath`, `notes` FROM `history_entries`
            """)
            db.execSQL("DROP TABLE `history_entries`")
            db.execSQL("ALTER TABLE `history_entries_new` RENAME TO `history_entries`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_entries_mole_id` ON `history_entries` (`mole_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_history_date` ON `history_entries` (`date`)")
            
            // Migrate background_variants dateAdded from TEXT (ISO8601) to INTEGER (Epoch Days)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `background_variants_new` (
                    `id` TEXT NOT NULL, 
                    `categoryId` TEXT NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `imagePath` TEXT, 
                    `orderIndex` INTEGER NOT NULL, 
                    `dateAdded` INTEGER NOT NULL, 
                    `notes` TEXT, 
                    PRIMARY KEY(`id`), 
                    FOREIGN KEY(`categoryId`) REFERENCES `background_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                )
            """)
            db.execSQL("""
                INSERT INTO `background_variants_new` (`id`, `categoryId`, `name`, `imagePath`, `orderIndex`, `dateAdded`, `notes`)
                SELECT `id`, `categoryId`, `name`, `imagePath`, `orderIndex`, CAST((julianday(`dateAdded`) - 2440587.5) AS INTEGER), `notes` FROM `background_variants`
            """)
            db.execSQL("DROP TABLE `background_variants`")
            db.execSQL("ALTER TABLE `background_variants_new` RENAME TO `background_variants`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_bg_variants_category` ON `background_variants` (`categoryId`)")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabaseRoom {
        return Room.databaseBuilder(
            context,
            AppDatabaseRoom::class.java,
            "Skin History Scanner_db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()
    }

    @Provides
    fun provideMoleDao(database: AppDatabaseRoom): MoleDao {
        return database.moleDao()
    }

    @Provides
    fun provideBackgroundDao(database: AppDatabaseRoom): com.example.chronomapscanner.data.local.room.BackgroundDao {
        return database.backgroundDao()
    }
}
