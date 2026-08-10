/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.tsuyomi.core.database.room.BookEntity
import org.tsuyomi.core.database.room.BrowsingHistoryEntity
import org.tsuyomi.core.database.room.CollectionEntity
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.LibraryEntryEntity
import org.tsuyomi.core.database.room.LocalBookTagEntity
import org.tsuyomi.core.database.room.ManualCollectionMembershipEntity
import org.tsuyomi.core.database.room.ImportSessionEntity
import org.tsuyomi.core.database.room.ImportWarningEntity
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.core.database.room.RemoteLibraryReconciliationEntity
import org.tsuyomi.core.database.room.RoomConverters
import org.tsuyomi.core.database.room.SearchHistoryEntity
import org.tsuyomi.core.database.room.SmartRuleEntity
import org.tsuyomi.core.database.room.SubscriptionDraftEntity
import org.tsuyomi.core.database.room.SourceAvailabilityEntity
import org.tsuyomi.core.database.room.SourceRemotePolicyEntity

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        LibraryEntryEntity::class,
        LocalBookTagEntity::class,
        ManualCollectionMembershipEntity::class,
        ReadingProgressEntity::class,
        RemoteLibraryReconciliationEntity::class,
        SourceAvailabilityEntity::class,
        SourceRemotePolicyEntity::class,
        BrowsingHistoryEntity::class,
        ImportSessionEntity::class,
        ImportWarningEntity::class,
        SearchHistoryEntity::class,
        SmartRuleEntity::class,
        SubscriptionDraftEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class TsuyomiDatabase : RoomDatabase() {
    /** The DAO is internal so Room implementation identifiers cannot become application contracts. */
    internal abstract fun libraryDao(): LibraryDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN authors_json TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE books ADD COLUMN author_sort_key BLOB")
        db.execSQL("ALTER TABLE books ADD COLUMN cover_url TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN canonical_url TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN status TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN remote_tags_json TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE books ADD COLUMN source_update_key TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN has_unread_update INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collections ADD COLUMN created_at_epoch_second INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collections ADD COLUMN created_at_nano INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collections ADD COLUMN updated_at_epoch_second INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collections ADD COLUMN updated_at_nano INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_entries (source_id TEXT NOT NULL, remote_book_id TEXT NOT NULL, added_at_epoch_second INTEGER NOT NULL, added_at_nano INTEGER NOT NULL, rating INTEGER, PRIMARY KEY(source_id, remote_book_id), FOREIGN KEY(source_id, remote_book_id) REFERENCES books(source_id, remote_book_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("INSERT OR IGNORE INTO library_entries(source_id, remote_book_id, added_at_epoch_second, added_at_nano, rating) SELECT DISTINCT m.source_id, m.remote_book_id, b.added_at_epoch_second, b.added_at_nano, NULL FROM manual_collection_memberships m JOIN books b ON b.source_id=m.source_id AND b.remote_book_id=m.remote_book_id")
        db.execSQL("CREATE TABLE manual_collection_memberships_new (collection_id TEXT NOT NULL, source_id TEXT NOT NULL, remote_book_id TEXT NOT NULL, added_at_epoch_second INTEGER NOT NULL, added_at_nano INTEGER NOT NULL, display_order INTEGER NOT NULL, PRIMARY KEY(collection_id, source_id, remote_book_id), FOREIGN KEY(collection_id) REFERENCES collections(collection_id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(source_id, remote_book_id) REFERENCES library_entries(source_id, remote_book_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("""INSERT INTO manual_collection_memberships_new(collection_id,source_id,remote_book_id,added_at_epoch_second,added_at_nano,display_order) SELECT m.collection_id,m.source_id,m.remote_book_id,b.added_at_epoch_second,b.added_at_nano,(SELECT COUNT(*) FROM manual_collection_memberships m2 JOIN books b2 ON b2.source_id=m2.source_id AND b2.remote_book_id=m2.remote_book_id WHERE m2.collection_id=m.collection_id AND (b2.added_at_epoch_second < b.added_at_epoch_second OR (b2.added_at_epoch_second=b.added_at_epoch_second AND b2.added_at_nano < b.added_at_nano) OR (b2.added_at_epoch_second=b.added_at_epoch_second AND b2.added_at_nano=b.added_at_nano AND (m2.source_id < m.source_id OR (m2.source_id=m.source_id AND m2.remote_book_id < m.remote_book_id))))) FROM manual_collection_memberships m JOIN books b ON b.source_id=m.source_id AND b.remote_book_id=m.remote_book_id""")
        db.execSQL("DROP TABLE manual_collection_memberships")
        db.execSQL("ALTER TABLE manual_collection_memberships_new RENAME TO manual_collection_memberships")
        db.execSQL("CREATE INDEX index_manual_collection_memberships_source_id_remote_book_id ON manual_collection_memberships(source_id, remote_book_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS local_book_tags (source_id TEXT NOT NULL, remote_book_id TEXT NOT NULL, normalized_tag TEXT NOT NULL, display_tag TEXT NOT NULL, PRIMARY KEY(source_id, remote_book_id, normalized_tag), FOREIGN KEY(source_id, remote_book_id) REFERENCES library_entries(source_id, remote_book_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS source_availability (source_id TEXT NOT NULL, verified_version TEXT, available INTEGER NOT NULL, generation INTEGER NOT NULL, PRIMARY KEY(source_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS source_remote_policy (source_id TEXT NOT NULL, trusted_publisher_fingerprint TEXT NOT NULL, capability_set_fingerprint TEXT NOT NULL, approved_origin TEXT NOT NULL, add_writeback_enabled INTEGER NOT NULL, first_import_prompt_dismissed INTEGER NOT NULL, PRIMARY KEY(source_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS remote_library_reconciliation (id TEXT NOT NULL, source_id TEXT NOT NULL, remote_book_id TEXT NOT NULL, package_digest TEXT NOT NULL, package_version TEXT NOT NULL, capability_set_fingerprint TEXT NOT NULL, registry_generation INTEGER NOT NULL, state TEXT NOT NULL, created_at_epoch_second INTEGER NOT NULL, updated_at_epoch_second INTEGER NOT NULL, diagnostic_id TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX index_remote_library_reconciliation_source_id_remote_book_id ON remote_library_reconciliation(source_id, remote_book_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS smart_rules (collection_id TEXT NOT NULL, rule_version INTEGER NOT NULL, ast_json TEXT NOT NULL, compiled_projection_version INTEGER NOT NULL, PRIMARY KEY(collection_id), FOREIGN KEY(collection_id) REFERENCES collections(collection_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS subscription_drafts (collection_id TEXT NOT NULL, mode TEXT NOT NULL, source_scope_json TEXT NOT NULL, query_json TEXT NOT NULL, enabled INTEGER NOT NULL, import_session_id TEXT, PRIMARY KEY(collection_id), FOREIGN KEY(collection_id) REFERENCES collections(collection_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS search_history (source_id TEXT NOT NULL, normalized_query TEXT NOT NULL, display_query TEXT NOT NULL, last_used_at_epoch_second INTEGER NOT NULL, last_used_at_nano INTEGER NOT NULL, PRIMARY KEY(source_id, normalized_query))")
        db.execSQL("CREATE TABLE IF NOT EXISTS browsing_history (source_id TEXT NOT NULL, remote_book_id TEXT NOT NULL, last_viewed_at_epoch_second INTEGER NOT NULL, last_viewed_at_nano INTEGER NOT NULL, PRIMARY KEY(source_id, remote_book_id), FOREIGN KEY(source_id, remote_book_id) REFERENCES books(source_id, remote_book_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS import_sessions (id TEXT NOT NULL, kind TEXT NOT NULL, plan_digest TEXT NOT NULL, normalized_plan_path TEXT NOT NULL, status TEXT NOT NULL, source_created_at_epoch_second INTEGER NOT NULL, started_at_epoch_second INTEGER NOT NULL, completed_at_epoch_second INTEGER, preference_patch_json TEXT NOT NULL, summary_json TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS import_warnings (session_id TEXT NOT NULL, ordinal INTEGER NOT NULL, safe_code TEXT NOT NULL, safe_record_ref TEXT, field_name TEXT, severity TEXT NOT NULL, PRIMARY KEY(session_id, ordinal), FOREIGN KEY(session_id) REFERENCES import_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
