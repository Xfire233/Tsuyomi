/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavBackStackEntry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tsuyomi.feature.library.SmartDraftNode
import org.tsuyomi.shared.model.BookIdentity

private const val DraftIdKey = "collection.rule.draft-id"
private const val DraftRevisionKey = "collection.rule.revision"

internal fun ruleDraftDigest(encoded: String): String = MessageDigest.getInstance("SHA-256")
    .digest(encoded.toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }

/** The only SavedStateHandle payload is a UUID and a revision. The complete editor state lives in an atomic app-private file. */
internal data class RuleRouteDraft(
    val collectionId: String?,
    val title: String,
    val encodedTree: String,
    val tree: SmartDraftNode.Group,
    val originalTitle: String,
    val originalDigest: String,
    val focus: String = "",
    val attempted: Boolean = false,
    val advancedExpanded: Boolean = false,
    val selectedBooks: Set<BookIdentity> = emptySet(),
    val booksBeforePick: Set<BookIdentity> = emptySet(),
    val revision: Int = 0,
)

internal class CollectionRuleDraftOwner(
    context: Context,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val directory = File(context.filesDir, "collection-rule-drafts")
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val writeSignal = Channel<Unit>(Channel.CONFLATED)
    private val writer: Job = ioScope.launch { for (ignored in writeSignal) persistLatest() }
    @Volatile private var committedRevision = -1
    @Volatile private var closed = false
    @Volatile private var activeId: String? = saved.get(DraftIdKey)
    var draft by mutableStateOf<RuleRouteDraft?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    val id: String get() = requireNotNull(activeId)

    private fun file(id: String): AtomicFile {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return AtomicFile(File(directory, "$id.json"))
    }

    suspend fun open(collectionId: String?, initial: suspend () -> CollectionRuleDraft?) {
        if (draft != null || failed || closed) return
        val restoredId = saved.get<String>(DraftIdKey)
        if (restoredId != null) {
            try {
                val restored = withContext(Dispatchers.IO) { read(file(restoredId)) }
                check(restored.collectionId == collectionId && restored.revision >= (saved.get<Int>(DraftRevisionKey) ?: 0))
                committedRevision = restored.revision
                draft = restored
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true // Never initialize from the original rule over a missing/corrupt active draft.
            }
            return
        }
        try {
            val source = initial() ?: run { failed = true; return }
            val opened = withContext(Dispatchers.IO) {
                val encoded = encodeRuleDraft(source.tree).toString()
                RuleRouteDraft(collectionId, source.title, encoded, source.tree, source.title,
                    ruleDraftDigest(encoded))
            }
            val id = UUID.randomUUID().toString()
            activeId = id
            withContext(Dispatchers.IO) {
                writeMutex.withLock { write(file(id), opened) }
            }
            committedRevision = 0
            if (closed) {
                withContext(Dispatchers.IO) { file(id).delete() }
                return
            }
            saved[DraftIdKey] = id
            saved[DraftRevisionKey] = 0
            draft = opened
        } catch (cancelled: CancellationException) {
            if (saved.get<String>(DraftIdKey) == null) activeId?.let { abandoned ->
                withContext(NonCancellable + Dispatchers.IO) {
                    writeMutex.withLock { file(abandoned).delete() }
                }
                if (!closed && activeId == abandoned) activeId = null
            }
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }

    fun edit(change: (RuleRouteDraft) -> RuleRouteDraft) {
        val current = draft ?: return
        if (closed) return
        val next = change(current).copy(revision = current.revision + 1)
        draft = next
        saved[DraftRevisionKey] = next.revision
        writeSignal.trySend(Unit)
    }

    /** Persist a frozen revision before committing the collection. A failed write never permits save. */
    suspend fun flush(): Boolean = withContext(Dispatchers.IO) {
        try {
            writeMutex.withLock { persistLocked() }
            !failed && draft?.revision == committedRevision && !closed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
            false
        }
    }

    /** Android may kill a stopped process immediately; wait for pending IO before Pause/Stop returns. */
    fun flushOnPause() {
        if (closed || draft == null || draft?.revision == committedRevision) return
        runBlocking { flush() } // flush switches to IO; the main thread never serializes or writes the tree.
    }

    private suspend fun persistLatest() {
        try {
            writeMutex.withLock { persistLocked() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }

    private fun persistLocked() {
        val id = activeId ?: error("Missing rule draft ID")
        val target = file(id)
        while (!closed) {
            val next = draft ?: return
            if (next.revision == committedRevision) return
            write(target, next)
            committedRevision = next.revision
            failed = false
        }
    }

    /** Safe to call before popping; the serialized deletion cannot race the last in-flight write. */
    fun discard() {
        if (closed) return
        closed = true
        writeSignal.close()
        val id = activeId ?: return
        ioScope.launch {
            writeMutex.withLock { file(id).delete() }
        }
        saved.remove<String>(DraftIdKey)
        saved.remove<Int>(DraftRevisionKey)
    }

    override fun onCleared() {
        discard() // A popped route cannot resurrect an orphan draft; configuration changes retain the ViewModel.
        super.onCleared()
    }

    private fun write(target: AtomicFile, value: RuleRouteDraft) {
        directory.mkdirs()
        val json = JSONObject().apply {
            put("collectionId", value.collectionId ?: JSONObject.NULL)
            put("title", value.title)
            put("tree", value.encodedTree)
            put("originalTitle", value.originalTitle)
            put("originalDigest", value.originalDigest)
            put("focus", value.focus)
            put("advancedExpanded", value.advancedExpanded)
            put("selectedBooks", org.json.JSONArray().apply {
                value.selectedBooks.sorted().forEach { identity ->
                    put(JSONObject().put("sourceId", identity.sourceId).put("remoteBookId", identity.remoteBookId))
                }
            })
            put("booksBeforePick", org.json.JSONArray().apply {
                value.booksBeforePick.sorted().forEach { identity ->
                    put(JSONObject().put("sourceId", identity.sourceId).put("remoteBookId", identity.remoteBookId))
                }
            })
            put("attempted", value.attempted)
            put("revision", value.revision)
        }.toString().toByteArray(Charsets.UTF_8)
        val stream = target.startWrite()
        try {
            stream.write(json)
            target.finishWrite(stream)
        } catch (failure: Exception) {
            target.failWrite(stream)
            throw failure
        }
    }

    private fun read(target: AtomicFile): RuleRouteDraft {
        val json = JSONObject(target.readFully().toString(Charsets.UTF_8))
        val encoded = json.getString("tree")
        val tree = decodeRuleDraft(JSONObject(encoded)) as SmartDraftNode.Group
        val originalDigest = json.getString("originalDigest")
        require(originalDigest.length == 64 && originalDigest.all { it in '0'..'9' || it in 'a'..'f' })
        return RuleRouteDraft(
            collectionId = if (json.isNull("collectionId")) null else json.getString("collectionId"),
            title = json.getString("title"),
            encodedTree = encoded,
            tree = tree,
            originalTitle = json.getString("originalTitle"),
            originalDigest = originalDigest,
            focus = json.getString("focus").also { focus ->
                require(focus.isEmpty() || focus.split('/').all { part -> part.toIntOrNull()?.let { it >= 0 } == true })
            },
            attempted = json.getBoolean("attempted"),
            revision = json.getInt("revision").also { require(it >= 0) },
            advancedExpanded = json.optBoolean("advancedExpanded", false),
            selectedBooks = json.optJSONArray("selectedBooks")?.let { books ->
                buildSet {
                    repeat(books.length()) { index ->
                        val book = books.getJSONObject(index)
                        add(BookIdentity(book.getString("sourceId"), book.getString("remoteBookId")))
                    }
                    require(size == books.length())
                }
            } ?: emptySet(),
            booksBeforePick = json.optJSONArray("booksBeforePick")?.let { books ->
                buildSet {
                    repeat(books.length()) { index ->
                        val book = books.getJSONObject(index)
                        add(BookIdentity(book.getString("sourceId"), book.getString("remoteBookId")))
                    }
                    require(size == books.length())
                }
            } ?: emptySet(),
        )
    }
}

private class CollectionRuleDraftOwnerFactory(
    private val context: Context,
    private val saved: SavedStateHandle,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == CollectionRuleDraftOwner::class.java)
        @Suppress("UNCHECKED_CAST")
        return CollectionRuleDraftOwner(context, saved) as T
    }
}

internal fun collectionRuleDraftOwner(entry: NavBackStackEntry, context: Context): CollectionRuleDraftOwner =
    ViewModelProvider(entry, CollectionRuleDraftOwnerFactory(context.applicationContext, entry.savedStateHandle))
        .get(CollectionRuleDraftOwner::class.java)
