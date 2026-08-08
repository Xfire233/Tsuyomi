/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.tsuyomi.core.database.room.BookEntity
import org.tsuyomi.core.database.room.CollectionEntity
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.ManualCollectionMembershipEntity
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.core.database.room.RoomConverters

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        ManualCollectionMembershipEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class TsuyomiDatabase : RoomDatabase() {
    /** The DAO is internal so Room implementation identifiers cannot become application contracts. */
    internal abstract fun libraryDao(): LibraryDao
}
