/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
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

    @Test
    fun migration_8_to_9_adds_unresolved_update_state_without_mutating_existing_library_data() {
        helper.createDatabase(UPDATE_STATE_DATABASE, 8).use { db ->
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','book-42','章节状态','[]','[]',0,10,0,10,0)",
            )
            db.execSQL(
                "INSERT INTO completed_chapters(source_id,remote_book_id,chapter_id,completed_at_epoch_second,completed_at_nano) " +
                    "VALUES ('fixture.source','book-42','chapter-2',20,0)",
            )
        }

        helper.runMigrationsAndValidate(UPDATE_STATE_DATABASE, 9, true, MIGRATION_8_9).use { db ->
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                    "('update_sessions','update_session_items','update_baselines','unresolved_updates','update_policy','update_book_exclusions','update_source_exclusions','update_ignore_undos')",
            ).use { cursor -> assertEquals(8, cursor.count) }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('update_inbox_rows','update_ack_undos')",
            ).use { cursor -> assertEquals(0, cursor.count) }
            db.query("SELECT chapter_id FROM completed_chapters WHERE source_id='fixture.source' AND remote_book_id='book-42'").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("chapter-2", cursor.getString(0))
            }
            db.execSQL(
                "INSERT INTO update_sessions(session_id,trigger,state,total,completed,updated,failed,reason,lease_expires_at_millis,lease_owner_token,cancellation_requested,started_at_millis,finished_at_millis) " +
                    "VALUES ('session-1','manual','RUNNING',1,0,0,0,NULL,1000,'owner-1',0,1,NULL)",
            )
            db.query("SELECT lease_owner_token, cancellation_requested FROM update_sessions WHERE session_id='session-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("owner-1", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migration_9_to_10_preserves_v9_library_data_and_validates_room_schema() {
        helper.createDatabase(LOCAL_PIN_DATABASE, 9).use { db ->
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,author_sort_key,cover_url,canonical_url,status,remote_tags_json,source_update_key,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','retained-42','保留状态','[\"作者\"]',X'0102','https://example.com/cover','https://example.com/book','ongoing','[\"远程\"]','legacy-key',0,10,2,20,3)",
            )
            db.execSQL(
                "INSERT INTO library_entries(source_id,remote_book_id,added_at_epoch_second,added_at_nano,rating,read_later,display_order) " +
                    "VALUES ('fixture.source','retained-42',10,2,4,1,7)",
            )
            db.execSQL(
                "INSERT INTO collections(collection_id,kind,title,parent_collection_id,display_order,created_at_epoch_second,created_at_nano,updated_at_epoch_second,updated_at_nano) " +
                    "VALUES ('favorites','MANUAL','收藏',NULL,0,1,2,3,4)",
            )
            db.execSQL(
                "INSERT INTO manual_collection_memberships(collection_id,source_id,remote_book_id,added_at_epoch_second,added_at_nano,display_order) " +
                    "VALUES ('favorites','fixture.source','retained-42',10,2,0)",
            )
            db.execSQL(
                "INSERT INTO local_book_tags(source_id,remote_book_id,normalized_tag,display_tag) " +
                    "VALUES ('fixture.source','retained-42','保留','保留')",
            )
            db.execSQL(
                "INSERT INTO reading_progress(source_id,remote_book_id,content_id,revision,block_id,text_anchor_digest,character_offset,chapter_progress,book_progress,updated_at_epoch_second,updated_at_nano) " +
                    "VALUES ('fixture.source','retained-42','chapter-7','rev-1','block-7',NULL,23,0.4,0.4,30,4)",
            )
            db.execSQL(
                "INSERT INTO completed_chapters(source_id,remote_book_id,chapter_id,completed_at_epoch_second,completed_at_nano) " +
                    "VALUES ('fixture.source','retained-42','chapter-7',31,5)",
            )
        }

        helper.runMigrationsAndValidate(LOCAL_PIN_DATABASE, 10, true, MIGRATION_9_10).use { db ->
            db.query(
                "SELECT rating,read_later,display_order,local_pin FROM library_entries WHERE source_id='fixture.source' AND remote_book_id='retained-42'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(4, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(7, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
            }
            db.query(
                "SELECT title,authors_json,author_sort_key,cover_url,canonical_url,status,remote_tags_json,source_update_key,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano FROM books WHERE source_id='fixture.source' AND remote_book_id='retained-42'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("保留状态", cursor.getString(0))
                assertEquals("[\"作者\"]", cursor.getString(1))
                assertEquals(byteArrayOf(1, 2).toList(), cursor.getBlob(2).toList())
                assertEquals("https://example.com/cover", cursor.getString(3))
                assertEquals("https://example.com/book", cursor.getString(4))
                assertEquals("ongoing", cursor.getString(5))
                assertEquals("[\"远程\"]", cursor.getString(6))
                assertEquals("legacy-key", cursor.getString(7))
                assertEquals(10L, cursor.getLong(8))
                assertEquals(2, cursor.getInt(9))
                assertEquals(20L, cursor.getLong(10))
                assertEquals(3, cursor.getInt(11))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
            db.query("SELECT COUNT(*) FROM manual_collection_memberships WHERE collection_id='favorites'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM local_book_tags WHERE normalized_tag='保留'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM reading_progress WHERE content_id='chapter-7'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM completed_chapters WHERE chapter_id='chapter-7'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }


    @Test
    fun migration_10_to_11_adds_empty_bookmark_store_without_mutating_existing_data() {
        helper.createDatabase(BOOKMARK_DATABASE, 10).use { db ->
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','retained-42','保留状态','[]','[]',0,10,0,20,0)",
            )
            db.execSQL(
                "INSERT INTO library_entries(source_id,remote_book_id,added_at_epoch_second,added_at_nano,rating,read_later,display_order,local_pin) " +
                    "VALUES ('fixture.source','retained-42',10,0,NULL,0,0,1)",
            )
            db.execSQL(
                "INSERT INTO completed_chapters(source_id,remote_book_id,chapter_id,completed_at_epoch_second,completed_at_nano) " +
                    "VALUES ('fixture.source','retained-42','completed',30,0)",
            )
        }

        helper.runMigrationsAndValidate(BOOKMARK_DATABASE, 11, true, MIGRATION_10_11).use { db ->
            db.query("SELECT COUNT(*) FROM chapter_bookmarks").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.execSQL(
                "INSERT INTO chapter_bookmarks(source_id,remote_book_id,chapter_id) VALUES ('fixture.source','retained-42','bookmark')",
            )
            db.query("SELECT title FROM books WHERE source_id='fixture.source' AND remote_book_id='retained-42'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("保留状态", cursor.getString(0))
            }
            db.query("SELECT chapter_id FROM completed_chapters WHERE source_id='fixture.source' AND remote_book_id='retained-42'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("completed", cursor.getString(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }
    @Test
    fun migration_11_to_12_converts_chapter_marks_to_degraded_chapter_start_locators() {
        helper.createDatabase(SEMANTIC_BOOKMARK_DATABASE, 11).use { db ->
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','retained-42','保留状态','[]','[]',0,10,0,20,0)",
            )
            db.execSQL(
                "INSERT INTO chapter_bookmarks(source_id,remote_book_id,chapter_id) VALUES ('fixture.source','retained-42','bookmark')",
            )
        }

        helper.runMigrationsAndValidate(SEMANTIC_BOOKMARK_DATABASE, 12, true, MIGRATION_11_12).use { db ->
            db.query(
                "SELECT content_id,revision,block_id,text_anchor_digest,character_offset,chapter_progress,book_progress,captured_at_epoch_second,captured_at_nano " +
                    "FROM reader_bookmarks WHERE source_id='fixture.source' AND remote_book_id='retained-42'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("bookmark", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
                assertEquals(0.0, cursor.getDouble(5), 0.0)
                assertTrue(cursor.isNull(6))
                assertEquals(0L, cursor.getLong(7))
                assertEquals(0, cursor.getInt(8))
                assertEquals(
                    LocatorPrecision.DEGRADED,
                    ReaderLocator(
                        document = DocumentIdentity("fixture.source", "retained-42", cursor.getString(0), cursor.getString(1)),
                        blockId = cursor.getString(2),
                        textAnchorDigest = cursor.getString(3),
                        characterOffset = if (cursor.isNull(4)) null else cursor.getInt(4),
                        chapterProgress = cursor.getDouble(5),
                        bookProgress = if (cursor.isNull(6)) null else cursor.getDouble(6),
                        capturedAt = Instant.ofEpochSecond(cursor.getLong(7), cursor.getInt(8).toLong()),
                    ).precision,
                )
            }
            db.query("SELECT bookmark_position_key FROM reader_bookmarks WHERE source_id='fixture.source' AND remote_book_id='retained-42'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("v1|D|666978747572652E736F75726365|72657461696E65642D3432|626F6F6B6D61726B|-|P|-|0|-", cursor.getString(0))
            }

            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='chapter_bookmarks'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT title FROM books WHERE source_id='fixture.source' AND remote_book_id='retained-42'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("保留状态", cursor.getString(0))
            }
        }
    }

    @Test
    fun migration_12_to_13_adds_empty_reader_history_without_inventing_visits() {
        helper.createDatabase(READER_HISTORY_DATABASE, 12).use { db ->
            db.execSQL(
                "INSERT INTO books(source_id,remote_book_id,title,authors_json,remote_tags_json,has_unread_update,added_at_epoch_second,added_at_nano,metadata_updated_at_epoch_second,metadata_updated_at_nano) " +
                    "VALUES ('fixture.source','retained-42','保留进度','[]','[]',0,10,0,20,0)",
            )
            db.execSQL(
                "INSERT INTO reading_progress(source_id,remote_book_id,content_id,revision,block_id,text_anchor_digest,character_offset,chapter_progress,book_progress,updated_at_epoch_second,updated_at_nano) " +
                    "VALUES ('fixture.source','retained-42','chapter-9',NULL,'block-1',NULL,12,NULL,1.0,99,7)",
            )
        }

        helper.runMigrationsAndValidate(READER_HISTORY_DATABASE, 13, true, MIGRATION_12_13).use { db ->
            db.query("SELECT COUNT(*) FROM reader_history").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT content_id,book_progress,updated_at_epoch_second,updated_at_nano FROM reading_progress").use { cursor ->
                cursor.moveToFirst()
                assertEquals("chapter-9", cursor.getString(0))
                assertEquals(1.0, cursor.getDouble(1), 0.0)
                assertEquals(99L, cursor.getLong(2))
                assertEquals(7, cursor.getInt(3))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
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
        const val UPDATE_STATE_DATABASE = "phase4c-update-state"
        const val LOCAL_PIN_DATABASE = "local-pin-migration"
        const val BOOKMARK_DATABASE = "chapter-bookmark-migration"
        const val SEMANTIC_BOOKMARK_DATABASE = "semantic-bookmark-migration"
        const val READER_HISTORY_DATABASE = "reader-history-migration"
    }
}
