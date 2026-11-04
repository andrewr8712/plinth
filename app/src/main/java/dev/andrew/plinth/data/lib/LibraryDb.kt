package dev.andrew.plinth.data.lib

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class, AlbumEntity::class], version = 1, exportSchema = false)
abstract class LibraryDb : RoomDatabase() {

    abstract fun dao(): LibraryDao

    companion object {
        fun build(context: Context): LibraryDb =
            Room.databaseBuilder(context, LibraryDb::class.java, "library.db").build()
    }
}
