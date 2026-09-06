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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase3MigrationInstrumentedTest {
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

    @Test
    fun read_later_defaults_false_when_migrating_existing_library_entries() {
        helper.createDatabase(READ_LATER_DATABASE, 2).use { db ->
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','book-42','旧书名','[]','[]',0,10,1,20,2)",
            )
            db.execSQL("INSERT INTO library_entries VALUES ('fixture.source','book-42',10,1,NULL)")
        }

        helper.runMigrationsAndValidate(READ_LATER_DATABASE, 3, true, MIGRATION_2_3).use { db ->
            db.query(
                "SELECT read_later FROM library_entries WHERE source_id='fixture.source' AND remote_book_id='book-42'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun library_display_order_backfills_existing_row_order() {
        helper.createDatabase(LIBRARY_ORDER_DATABASE, 3).use { db ->
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','book-b','第二本','[]','[]',0,20,0,20,0)",
            )
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','book-a','第一本','[]','[]',0,10,0,10,0)",
            )
            db.execSQL("INSERT INTO library_entries VALUES ('fixture.source','book-b',20,0,NULL,0)")
            db.execSQL("INSERT INTO library_entries VALUES ('fixture.source','book-a',10,0,NULL,0)")
        }

        helper.runMigrationsAndValidate(LIBRARY_ORDER_DATABASE, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT remote_book_id, display_order FROM library_entries ORDER BY display_order").use { cursor ->
                cursor.moveToFirst()
                assertEquals("book-b", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                cursor.moveToNext()
                assertEquals("book-a", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migration_4_to_5_adds_remote_mutation_and_writeback_columns() {
        helper.createDatabase(PHASE4B_DATABASE, 4).use { db ->
            db.execSQL(
                "INSERT INTO source_remote_policy(source_id,trusted_publisher_fingerprint,capability_set_fingerprint,approved_origin,add_writeback_enabled,first_import_prompt_dismissed) " +
                    "VALUES ('fixture.source','pub-1','cap-1','https://example.com',1,0)",
            )
            db.execSQL(
                "INSERT INTO remote_library_reconciliation(id,source_id,remote_book_id,package_digest,package_version,capability_set_fingerprint,registry_generation,state,created_at_epoch_second,updated_at_epoch_second,diagnostic_id) " +
                    "VALUES ('rec-1','fixture.source','book-1','pkg-1','1.0.0','cap-1',1,'IN_FLIGHT',100,100,NULL)",
            )
        }

        helper.runMigrationsAndValidate(PHASE4B_DATABASE, 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT remove_writeback_enabled, move_writeback_enabled FROM source_remote_policy WHERE source_id='fixture.source'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
            }
            db.query("SELECT operation, target_id, target_name FROM remote_library_reconciliation WHERE id='rec-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("ADD", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }

    @Test
    fun migration_5_to_6_adds_durable_remote_mirror_tables() {
        helper.createDatabase(REMOTE_MIRROR_DATABASE, 5).close()

        helper.runMigrationsAndValidate(REMOTE_MIRROR_DATABASE, 6, true, MIGRATION_5_6).use { db ->
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                    "('remote_mirror_bindings','remote_mirror_targets','remote_mirror_items') ORDER BY name",
            ).use { cursor ->
                assertEquals(3, cursor.count)
            }
            db.execSQL(
                "INSERT INTO remote_mirror_bindings(source_id,display_name,frozen,updated_at_epoch_second) " +
                    "VALUES ('fixture.source','示例书架',0,100)",
            )
            db.execSQL(
                "INSERT INTO remote_mirror_targets(source_id,target_id,display_name,parent_id,kind,frozen,updated_at_epoch_second) " +
                    "VALUES ('fixture.source','folder-1','收藏夹',NULL,'folder',0,100)",
            )
            db.query("SELECT display_name, frozen FROM remote_mirror_targets WHERE target_id='folder-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("收藏夹", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migration_6_to_7_never_infers_copy_consent_from_mirror_overlap() {
        helper.createDatabase(LOCAL_COPY_RECEIPT_DATABASE, 6).use { db ->
            db.execSQL(
                "INSERT INTO source_remote_policy VALUES " +
                    "('copied.source','pub-1','cap-1','https://example.com',0,0,0,0)," +
                    "('unused.source','pub-2','cap-2','https://example.org',0,0,0,0)",
            )
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('copied.source','book-1','既有本地副本','[]','[]',0,10,0,10,0)",
            )
            db.execSQL(
                "INSERT INTO library_entries(source_id,remote_book_id,added_at_epoch_second,added_at_nano,rating,read_later,display_order) " +
                    "VALUES ('copied.source','book-1',10,0,NULL,0,0)",
            )
            db.execSQL(
                "INSERT INTO remote_mirror_items(source_id,remote_book_id,target_id,updated_at_epoch_second) " +
                    "VALUES ('copied.source','book-1',NULL,10)",
            )
        }

        helper.runMigrationsAndValidate(LOCAL_COPY_RECEIPT_DATABASE, 7, true, MIGRATION_6_7).use { db ->
            db.query(
                "SELECT source_id, first_import_prompt_dismissed FROM source_remote_policy ORDER BY source_id",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("copied.source", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                cursor.moveToNext()
                assertEquals("unused.source", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migration_7_to_8_adds_exact_completed_chapter_state() {
        helper.createDatabase(EXACT_CHAPTER_STATE_DATABASE, 7).use { db ->
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','book-42','章节状态','[]','[]',0,10,0,10,0)",
            )
        }

        helper.runMigrationsAndValidate(EXACT_CHAPTER_STATE_DATABASE, 8, true, MIGRATION_7_8).use { db ->
            db.execSQL(
                "INSERT INTO completed_chapters(source_id,remote_book_id,chapter_id,completed_at_epoch_second,completed_at_nano) " +
                    "VALUES ('fixture.source','book-42','chapter-2',20,0)",
            )
            db.query("SELECT chapter_id FROM completed_chapters WHERE source_id='fixture.source' AND remote_book_id='book-42'").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("chapter-2", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val DATABASE = "phase3-migration"
        const val READ_LATER_DATABASE = "phase4a-read-later-migration"
        const val LIBRARY_ORDER_DATABASE = "phase4a-library-order-migration"
        const val PHASE4B_DATABASE = "phase4b-remote-writeback-migration"
        const val REMOTE_MIRROR_DATABASE = "phase4b-remote-mirror-migration"
        const val LOCAL_COPY_RECEIPT_DATABASE = "phase4b-local-copy-receipt-migration"
        const val EXACT_CHAPTER_STATE_DATABASE = "phase4b-exact-chapter-state-migration"
    }
}
