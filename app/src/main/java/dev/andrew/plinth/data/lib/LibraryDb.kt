package dev.andrew.plinth.data.lib

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class, AlbumEntity::class], version = 2, exportSchema = false)
abstract class LibraryDb : RoomDatabase() {

    abstract fun dao(): LibraryDao

    companion object {
        fun build(context: Context): LibraryDb =
            Room.databaseBuilder(context, LibraryDb::class.java, "library.db")
                // The cache is refilled from the streamer on every launch, so a schema
                // change costs one sync rather than a migration nobody will write twice.
                .fallbackToDestructiveMigration()
                .build()
    }
}
