package dev.andrew.plinth.data.lib

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackEntity::class, TrackFtsEntity::class, AlbumEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class LibraryDb : RoomDatabase() {

    abstract fun dao(): LibraryDao

    companion object {
        /**
         * Adds the search index to a database that already holds a cached library.
         * Room creates the external-content FTS table and its sync triggers only when
         * it creates the whole schema, so a migration has to write both itself; the
         * `rebuild` then fills the empty index from the `track` rows that are already
         * there. This is the same DDL Room emits for `TrackFtsEntity`, so the schema
         * Room validates after migrating matches.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `track_fts` USING FTS4(" +
                        "`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, " +
                        "content=`track`)",
                )
                // Without these, rows synced after the upgrade would never reach the
                // index. `wipeTracks` deletes fire the BEFORE DELETE trigger, and the
                // insert that follows fires AFTER INSERT.
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_track_fts_BEFORE_UPDATE " +
                        "BEFORE UPDATE ON `track` BEGIN DELETE FROM `track_fts` " +
                        "WHERE `docid`=OLD.`rowid`; END",
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_track_fts_BEFORE_DELETE " +
                        "BEFORE DELETE ON `track` BEGIN DELETE FROM `track_fts` " +
                        "WHERE `docid`=OLD.`rowid`; END",
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_track_fts_AFTER_UPDATE " +
                        "AFTER UPDATE ON `track` BEGIN INSERT INTO `track_fts`" +
                        "(`docid`, `title`, `artist`, `album`) VALUES " +
                        "(NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`); END",
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_track_fts_AFTER_INSERT " +
                        "AFTER INSERT ON `track` BEGIN INSERT INTO `track_fts`" +
                        "(`docid`, `title`, `artist`, `album`) VALUES " +
                        "(NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`); END",
                )
                db.execSQL("INSERT INTO `track_fts`(`track_fts`) VALUES('rebuild')")
            }
        }

        fun build(context: Context): LibraryDb =
            Room.databaseBuilder(context, LibraryDb::class.java, "library.db")
                .addMigrations(MIGRATION_2_3)
                // A database older than v2 has no migration path (its schema predates
                // the album table), so that one case still refills from the streamer.
                .fallbackToDestructiveMigration()
                .build()
    }
}
