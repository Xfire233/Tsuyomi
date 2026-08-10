/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gate3MigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(TsuyomiDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun manual_membership_backfills_explicit_library_entry_before_new_foreign_key() {
        helper.createDatabase(DATABASE, 1).use { db ->
            db.execSQL("INSERT INTO books VALUES ('fixture.source','book-42','旧书名',10,1,20,2)")
            db.execSQL("INSERT INTO books VALUES ('fixture.source','book-7','更早的书',5,0,8,0)")
            db.execSQL("INSERT INTO collections VALUES ('favorites','MANUAL','收藏',NULL,0)")
            db.execSQL("INSERT INTO manual_collection_memberships VALUES ('favorites','fixture.source','book-42')")
            db.execSQL("INSERT INTO manual_collection_memberships VALUES ('favorites','fixture.source','book-7')")
            db.execSQL("INSERT INTO reading_progress VALUES ('fixture.source','book-42','chapter-9','rev-1','block-3','anchor',17,0.4,0.25,99,7)")
        }

        helper.runMigrationsAndValidate(DATABASE, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT COUNT(*) FROM library_entries WHERE source_id='fixture.source' AND remote_book_id='book-42'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM manual_collection_memberships WHERE collection_id='favorites'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.query("SELECT authors_json, remote_tags_json, has_unread_update FROM books WHERE source_id='fixture.source' AND remote_book_id='book-42'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("[]", cursor.getString(0))
                assertEquals("[]", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
            db.query("SELECT remote_book_id, display_order FROM manual_collection_memberships WHERE collection_id='favorites' ORDER BY display_order").use { cursor ->
                cursor.moveToFirst()
                assertEquals("book-7", cursor.getString(0))
                assertEquals(0L, cursor.getLong(1))
                cursor.moveToNext()
                assertEquals("book-42", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
            }
            db.query("SELECT content_id, revision, block_id, character_offset, updated_at_epoch_second, updated_at_nano FROM reading_progress WHERE source_id='fixture.source' AND remote_book_id='book-42'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("chapter-9", cursor.getString(0))
                assertEquals("rev-1", cursor.getString(1))
                assertEquals("block-3", cursor.getString(2))
                assertEquals(17, cursor.getInt(3))
                assertEquals(99L, cursor.getLong(4))
                assertEquals(7, cursor.getInt(5))
            }
            db.query("SELECT created_at_epoch_second, updated_at_epoch_second FROM collections WHERE collection_id='favorites'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0L, cursor.getLong(0))
                assertEquals(0L, cursor.getLong(1))
            }
        }
    }

    private companion object {
        const val DATABASE = "gate3-migration"
    }
}
