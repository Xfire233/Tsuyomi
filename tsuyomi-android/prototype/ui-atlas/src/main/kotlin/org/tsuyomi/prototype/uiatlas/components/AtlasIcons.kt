/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*

/**
 * Single Material Icons façade for Atlas chrome and content actions.
 *
 * Call sites keep product-semantic names while every glyph comes from the Apache-2.0
 * Compose Material Icons library. Outlined icons are the default action style; explicit
 * filled/outlined state pairs remain available where selection state needs them.
 */
object AtlasIcons {
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val Close = Icons.Outlined.Close
    val Search = Icons.Outlined.Search
    val Add = Icons.Outlined.Add
    val Delete = Icons.Outlined.DeleteOutline
    val History = Icons.Outlined.History
    val SelectAll = Icons.Outlined.SelectAll
    val Deselect = Icons.Outlined.Deselect
    val LayoutList = Icons.AutoMirrored.Outlined.ViewList
    val LayoutCompact = Icons.Outlined.ViewHeadline
    val LayoutGrid = Icons.Outlined.GridView
    val Overflow = Icons.Outlined.MoreVert
    val ViewAll = Icons.Outlined.OpenInFull
    val Tune = Icons.Outlined.Tune
    val Shelf = Icons.Outlined.AutoStories
    val Folder = Icons.Outlined.Folder
    val FolderAdd = Icons.Outlined.CreateNewFolder
    val FolderMove = Icons.AutoMirrored.Outlined.DriveFileMove
    val FolderOpen = Icons.Outlined.FolderOpen
    val Document = Icons.Outlined.Description
    val ReadLater = Icons.Outlined.BookmarkBorder
    val Bookmarked = Icons.Outlined.BookmarkAdded
    val Updates = Icons.Outlined.NotificationsActive
    val Compass = Icons.Outlined.Explore
    val More = Icons.Outlined.MoreHoriz
    val Info = Icons.Outlined.Info
    val Warning = Icons.Outlined.WarningAmber
    val Check = Icons.Outlined.Check
    val Star = Icons.Outlined.Star
    val StarOutline = Icons.Outlined.StarBorder
    val Lock = Icons.Outlined.Lock
    val LockOpen = Icons.Outlined.LockOpen
    val Edit = Icons.Outlined.Edit
    val Collapse = Icons.Outlined.ExpandLess
    val Expand = Icons.Outlined.ExpandMore
    val MoveEarlier = Icons.AutoMirrored.Outlined.ArrowBack
    val MoveLater = Icons.AutoMirrored.Outlined.ArrowForward
    val Cache = Icons.Outlined.Download
    val Downloaded = Icons.Outlined.DownloadDone
    val Filter = Icons.Outlined.FilterList
    val Sort = Icons.AutoMirrored.Outlined.Sort
    val Refresh = Icons.Outlined.Refresh
    val CopyAll = Icons.Outlined.ContentCopy
    val Prev = Icons.Outlined.ChevronLeft
    val Next = Icons.Outlined.ChevronRight
    val Jump = Icons.AutoMirrored.Outlined.LastPage
    val Chapters = Icons.AutoMirrored.Outlined.FormatListBulleted
    val Settings = Icons.Outlined.Settings
    val Verify = Icons.Outlined.VerifiedUser
    val Up = Icons.Outlined.ArrowUpward
    val Down = Icons.Outlined.ArrowDownward
}
