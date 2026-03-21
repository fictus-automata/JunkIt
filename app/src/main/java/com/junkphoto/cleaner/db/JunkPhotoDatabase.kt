package com.junkphoto.cleaner.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [JunkPhotoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JunkPhotoDatabase : RoomDatabase() {

    abstract fun junkPhotoDao(): JunkPhotoDao

    companion object {
        @Volatile
        private var INSTANCE: JunkPhotoDatabase? = null

        fun getInstance(context: Context): JunkPhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JunkPhotoDatabase::class.java,
                    "junk_photos.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
