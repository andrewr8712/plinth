package dev.andrew.plinth.data.lib

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.andrew.plinth.data.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The upgrade an installed build performs: a v2 database already holds the cached
 * library and has no search index. Reopening must add the FTS table, fill it from
 * the rows that are already there, and pass Room's own schema validation.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryDbMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `a version 2 cache survives the search migration`() = runBlocking {
        // A library written by the current schema, then demoted to the v2 shape:
        // no FTS table and none of Room's sync triggers.
        val seeded = LibraryDb.build(context)
        try {
            LibraryRepository(seeded).replaceAll(
                listOf(
                    Track(
                        uri = "Mezzanine/01 - Angel.flac", albumId = "a1", disc = 1, trackNo = 1,
                        title = "Angel", artist = "Massive Attack", albumArtist = "Massive Attack",
                        album = "Mezzanine", year = 1998, genre = null, durationSec = 378,
                        format = null, path = "Mezzanine", channels = 2,
                    ),
                ),
            )
        } finally {
            seeded.close()
        }

        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB_NAME), null)
        try {
            raw.execSQL("DROP TABLE track_fts")
            for (trigger in TRIGGERS) raw.execSQL("DROP TRIGGER IF EXISTS $trigger")
            raw.version = 2
        } finally {
            raw.close()
        }

        // Reopening runs MIGRATION_2_3 and validates the migrated schema.
        val migrated = LibraryDb.build(context)
        try {
            val repo = LibraryRepository(migrated)
            assertEquals("the cached library must survive the upgrade", 1, repo.trackCount())
            assertEquals(listOf("Angel"), repo.search("ang").first().map { it.title })
            assertTrue("the index must still be a prefix index", repo.search("gel").first().isEmpty())
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val DB_NAME = "library.db"

        /** Room's external-content sync triggers, named after the FTS table. */
        val TRIGGERS = listOf(
            "room_fts_content_sync_track_fts_BEFORE_UPDATE",
            "room_fts_content_sync_track_fts_BEFORE_DELETE",
            "room_fts_content_sync_track_fts_AFTER_UPDATE",
            "room_fts_content_sync_track_fts_AFTER_INSERT",
        )
    }
}
