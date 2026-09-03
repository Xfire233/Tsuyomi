/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

/** Standard Material icon catalogue used by production UI. */
object TsuyomiIcons {
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Disclosure: ImageVector = Icons.Filled.ArrowDropDown
    val Shelf: ImageVector = Icons.AutoMirrored.Filled.MenuBook
    val Compass: ImageVector = Icons.Filled.Explore
    val More: ImageVector = Icons.Filled.MoreHoriz
    val Search: ImageVector = Icons.Filled.Search
    val Add: ImageVector = Icons.Filled.Add
    val Grid: ImageVector = Icons.Filled.GridView
    val List: ImageVector = Icons.AutoMirrored.Filled.ViewList
    val Compact: ImageVector = Icons.Filled.Reorder
    val Overflow: ImageVector = Icons.Filled.MoreVert
    val Refresh: ImageVector = Icons.Filled.Refresh
    val ContinueReading: ImageVector = Icons.Filled.PlayArrow
    val Recent: ImageVector = Icons.Filled.History
    val Bookmark: ImageVector = Icons.Filled.Bookmark
    val BookmarkOutline: ImageVector = Icons.Filled.BookmarkBorder
    val Selected: ImageVector = Icons.Filled.Check
    val Previous: ImageVector = Icons.AutoMirrored.Filled.NavigateBefore
    val Next: ImageVector = Icons.AutoMirrored.Filled.NavigateNext
    val Chapters: ImageVector = Icons.AutoMirrored.Filled.FormatListBulleted
    val Settings: ImageVector = Icons.Filled.Settings
    val Dormant: ImageVector = Icons.Filled.VisibilityOff
    val Folder: ImageVector = Icons.Filled.Folder
    val SmartCollection: ImageVector = Icons.Filled.AutoAwesome
    val Mirror: ImageVector = Icons.Filled.SyncAlt
    val Updates: ImageVector = Icons.Filled.Notifications
    val ViewAll: ImageVector = Icons.Filled.OpenInFull
    val Lock: ImageVector = Icons.Filled.Lock
    val LockOpen: ImageVector = Icons.Filled.LockOpen
    val Info: ImageVector = Icons.Filled.Info
    val Verify: ImageVector = Icons.Filled.VerifiedUser
    val Cache: ImageVector = Icons.Filled.Download
    val Star: ImageVector = Icons.Filled.Star
    val StarOutline: ImageVector = Icons.Filled.StarBorder
    val Filter: ImageVector = Icons.Filled.FilterList
    val Downloaded: ImageVector = Icons.Filled.DownloadDone
    val ToTop: ImageVector = Icons.Filled.ArrowUpward
    val ToBottom: ImageVector = Icons.Filled.ArrowDownward
    val Close: ImageVector = Icons.Filled.Close
    val Delete: ImageVector = Icons.Filled.Delete
    val CreateFolder: ImageVector = Icons.Filled.CreateNewFolder
    val MoveToFolder: ImageVector = Icons.AutoMirrored.Filled.DriveFileMove
    val SelectAll: ImageVector = Icons.Filled.SelectAll
    val DeselectAll: ImageVector = Icons.Filled.Deselect
}
