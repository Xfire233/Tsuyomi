/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database.room

import androidx.room.TypeConverter
import org.tsuyomi.core.database.CollectionKind

internal class RoomConverters {
    @TypeConverter
    fun collectionKindToStorage(value: CollectionKind): String = value.name

    @TypeConverter
    fun collectionKindFromStorage(value: String): CollectionKind = CollectionKind.valueOf(value)
}
