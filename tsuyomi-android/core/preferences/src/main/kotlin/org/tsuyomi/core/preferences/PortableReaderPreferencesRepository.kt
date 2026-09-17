/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.model.BookIdentity

data class RequestedReaderFlow(
    val flow: String,
    val overridden: Boolean,
)

class PortableReaderPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private val data: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
    val preferences: Flow<PortableReaderPreferences> = data
        .map { values ->
            PortableReaderPreferences(
                flow = values[FLOW] ?: DEFAULT_FLOW,
                fontScale = values[FONT_SCALE] ?: 1.0,
                lineHeight = values[LINE_HEIGHT] ?: 1.5,
                theme = values[THEME] ?: "paper",
                horizontalMargin = values[HORIZONTAL_MARGIN] ?: 24.0,
                paragraphSpacing = values[PARAGRAPH_SPACING] ?: 12.0,
                lockPortrait = values[LOCK_PORTRAIT] ?: false,
                progressVisible = values[PROGRESS_VISIBLE] ?: true,
                immersive = values[IMMERSIVE] ?: false,
                keepAwake = values[KEEP_AWAKE] ?: true,
                volumePaging = values[VOLUME_PAGING] ?: true,
                fontFamily = values[FONT_FAMILY] ?: DEFAULT_FONT_FAMILY,
                fontWeight = values[FONT_WEIGHT] ?: DEFAULT_FONT_WEIGHT,
                letterSpacing = values[LETTER_SPACING] ?: 0.0,
                firstLineIndent = values[FIRST_LINE_INDENT] ?: 0.0,
                verticalMargin = values[VERTICAL_MARGIN] ?: 24.0,
                textAlignment = values[TEXT_ALIGNMENT] ?: DEFAULT_TEXT_ALIGNMENT,
                foregroundColor = values[FOREGROUND_COLOR],
                backgroundColor = values[BACKGROUND_COLOR],
            )
        }

    val lastAppliedImportDigest: Flow<String?> = data.map { it[LAST_APPLIED_IMPORT_DIGEST] }

    fun requestedFlow(identity: BookIdentity, globalDefault: PortableReaderPreferences): Flow<RequestedReaderFlow> =
        data.map { values ->
            val default = values[FLOW]?.takeIf(::isRequestedFlow)
                ?: globalDefault.flow?.takeIf(::isRequestedFlow) ?: DEFAULT_FLOW
            val override = values[FLOW_OVERRIDES]
                .orEmpty()
                .asSequence()
                .mapNotNull(::decodeFlowOverride)
                .firstOrNull { (candidate, _) -> candidate == identity }
                ?.second
            RequestedReaderFlow(override ?: default, override != null)
        }

    suspend fun setFlowOverride(identity: BookIdentity, requested: String?) {
        requested?.let { require(isRequestedFlow(it)) { "Invalid reader flow override" } }
        dataStore.edit { values ->
            val retained = values[FLOW_OVERRIDES]
                .orEmpty()
                .filterNot { encoded -> decodeFlowOverride(encoded)?.first == identity }
                .toMutableSet()
            requested?.let {
                retained += encodeFlowOverride(identity, it)
                values[FLOW] = it
            }
            if (retained.isEmpty()) values.remove(FLOW_OVERRIDES) else values[FLOW_OVERRIDES] = retained
        }
    }

    suspend fun update(preferences: PortableReaderPreferences) {
        dataStore.edit { values -> applyReader(values, preferences, clearColorOverrides = true) }
    }

    suspend fun applyImport(
        preferences: PortableReaderPreferences?,
        forceManualEInk: Boolean,
        digest: String,
    ) {
        dataStore.edit { values ->
            preferences?.let { applyReader(values, it, clearColorOverrides = false) }
            if (forceManualEInk) values[DISPLAY_PREFERENCE] = "EINK"
            values[LAST_APPLIED_IMPORT_DIGEST] = digest
        }
    }

    private fun applyReader(
        values: androidx.datastore.preferences.core.MutablePreferences,
        preferences: PortableReaderPreferences,
        clearColorOverrides: Boolean,
    ) {
        preferences.flow?.let { require(isRequestedFlow(it)); values[FLOW] = it }
        preferences.fontScale?.let { require(it.isFinite() && it in 0.5..3.0); values[FONT_SCALE] = it }
        preferences.lineHeight?.let { require(it.isFinite() && it in 0.8..3.0); values[LINE_HEIGHT] = it }
        preferences.theme?.let { require(it in THEMES); values[THEME] = it }
        preferences.horizontalMargin?.let { require(it.isFinite() && it in 12.0..40.0); values[HORIZONTAL_MARGIN] = it }
        preferences.paragraphSpacing?.let { require(it.isFinite() && it in 0.0..32.0); values[PARAGRAPH_SPACING] = it }
        preferences.lockPortrait?.let { values[LOCK_PORTRAIT] = it }
        preferences.progressVisible?.let { values[PROGRESS_VISIBLE] = it }
        preferences.immersive?.let { values[IMMERSIVE] = it }
        preferences.keepAwake?.let { values[KEEP_AWAKE] = it }
        preferences.volumePaging?.let { values[VOLUME_PAGING] = it }
        preferences.fontFamily?.let { require(it in FONT_FAMILIES); values[FONT_FAMILY] = it }
        preferences.fontWeight?.let { require(it in FONT_WEIGHTS); values[FONT_WEIGHT] = it }
        preferences.letterSpacing?.let { require(it.isFinite() && it in -0.05..0.20); values[LETTER_SPACING] = it }
        preferences.firstLineIndent?.let { require(it.isFinite() && it in 0.0..4.0); values[FIRST_LINE_INDENT] = it }
        preferences.verticalMargin?.let { require(it.isFinite() && it in 0.0..64.0); values[VERTICAL_MARGIN] = it }
        preferences.textAlignment?.let { require(it in TEXT_ALIGNMENTS); values[TEXT_ALIGNMENT] = it }
        val foreground = preferences.foregroundColor
        if (foreground != null) {
            require(HEX_COLOR.matches(foreground))
            values[FOREGROUND_COLOR] = foreground
        } else if (clearColorOverrides) {
            values.remove(FOREGROUND_COLOR)
        }
        val background = preferences.backgroundColor
        if (background != null) {
            require(HEX_COLOR.matches(background))
            values[BACKGROUND_COLOR] = background
        } else if (clearColorOverrides) {
            values.remove(BACKGROUND_COLOR)
        }
    }


    private companion object {
        val FLOW = stringPreferencesKey("reader_flow")
        val FONT_SCALE = doublePreferencesKey("reader_font_scale")
        val LINE_HEIGHT = doublePreferencesKey("reader_line_height")
        val THEME = stringPreferencesKey("reader_theme")
        val HORIZONTAL_MARGIN = doublePreferencesKey("reader_horizontal_margin")
        val PARAGRAPH_SPACING = doublePreferencesKey("reader_paragraph_spacing")
        val LOCK_PORTRAIT = booleanPreferencesKey("reader_lock_portrait")
        val PROGRESS_VISIBLE = booleanPreferencesKey("reader_progress_visible")
        val IMMERSIVE = booleanPreferencesKey("reader_immersive")
        val KEEP_AWAKE = booleanPreferencesKey("reader_keep_awake")
        val VOLUME_PAGING = booleanPreferencesKey("reader_volume_paging")
        val FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val FONT_WEIGHT = intPreferencesKey("reader_font_weight")
        val LETTER_SPACING = doublePreferencesKey("reader_letter_spacing")
        val FIRST_LINE_INDENT = doublePreferencesKey("reader_first_line_indent")
        val VERTICAL_MARGIN = doublePreferencesKey("reader_vertical_margin")
        val TEXT_ALIGNMENT = stringPreferencesKey("reader_text_alignment")
        val FOREGROUND_COLOR = stringPreferencesKey("reader_foreground_color")
        val BACKGROUND_COLOR = stringPreferencesKey("reader_background_color")
        val FLOW_OVERRIDES = stringSetPreferencesKey("reader_flow_overrides_v1")
        val DISPLAY_PREFERENCE = stringPreferencesKey("display_preference")
        val LAST_APPLIED_IMPORT_DIGEST = stringPreferencesKey("last_applied_import_digest")
        val THEMES = setOf("paper", "warmGray", "nightInk", "black", "inkGreen")
        val FONT_FAMILIES = setOf("system", "sans", "serif", "monospace")
        val TEXT_ALIGNMENTS = setOf("start", "justify", "center", "end")
        val FONT_WEIGHTS = setOf(400, 500)
        val REQUESTED_FLOWS = setOf("scroll", "paged", "dual")
        val HEX_COLOR = Regex("^#[0-9A-F]{6}$")
        const val DEFAULT_FLOW = "scroll"
        const val DEFAULT_FONT_FAMILY = "system"
        const val DEFAULT_FONT_WEIGHT = 400
        const val DEFAULT_TEXT_ALIGNMENT = "start"

        fun isRequestedFlow(value: String): Boolean = value in REQUESTED_FLOWS

        fun encodeFlowOverride(identity: BookIdentity, flow: String): String =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString("${identity.sourceId}\u0000${identity.remoteBookId}".toByteArray(StandardCharsets.UTF_8)) + "|$flow"

        fun decodeFlowOverride(value: String): Pair<BookIdentity, String>? = runCatching {
            val separator = value.lastIndexOf('|')
            require(separator > 0)
            val identity = Base64.getUrlDecoder().decode(value.substring(0, separator))
                .toString(StandardCharsets.UTF_8)
                .split('\u0000', limit = 2)
            require(identity.size == 2 && isRequestedFlow(value.substring(separator + 1)))
            BookIdentity(identity[0], identity[1]) to value.substring(separator + 1)
        }.getOrNull()
    }
}
