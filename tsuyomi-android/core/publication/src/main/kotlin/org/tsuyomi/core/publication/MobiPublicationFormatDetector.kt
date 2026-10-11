/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat

/**
 * Detects only the outer publication family from bounded bytes. EPUB package validation and MOBI
 * record/content validation remain with their real parsers; TXT still passes strict decoding.
 */
fun detectPublicationFormat(
    source: PublicationSource,
    cancelled: () -> Unit = {},
): LocalPublicationFormat {
    if (source.sizeBytes !in 1..MAX_DETECTOR_ORIGINAL_BYTES) {
        throw PublicationParseException(PublicationFailureCode.ORIGINAL_LIMIT)
    }
    if (!SHA256_REGEX.matches(source.sha256)) throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL)
    val prefix = readFormatPrefix(source, cancelled)
    if (prefix.startsWithZipSignature()) return LocalPublicationFormat.EPUB
    if (prefix.size >= PDB_SIGNATURE_OFFSET + PDB_SIGNATURE.length &&
        prefix.matchesAscii(PDB_SIGNATURE_OFFSET, PDB_SIGNATURE)) {
        return detectMobiFormat(prefix, source.sizeBytes)
    }
    if (prefix.startsWithUnsupportedContainer()) throw PublicationParseException(
        PublicationFailureCode.UNSUPPORTED_FORMAT,
        "This local publication container is not EPUB, MOBI/KF8, AZW3, or text.",
    )
    if (!looksLikeText(prefix)) throw PublicationParseException(
        PublicationFailureCode.UNSUPPORTED_FORMAT,
        "The local file does not have a supported publication signature or plausible text content.",
    )
    return LocalPublicationFormat.TXT
}

private fun detectMobiFormat(prefix: ByteArray, originalSizeBytes: Long): LocalPublicationFormat {
    if (prefix.size < PDB_HEADER_BYTES) malformedMobi()
    val recordCount = prefix.readU16(PDB_RECORD_COUNT_OFFSET)
    if (recordCount == 0) malformedMobi()
    val tableEnd = PDB_RECORDS_OFFSET.toLong() + recordCount.toLong() * PDB_RECORD_ENTRY_BYTES
    if (tableEnd > prefix.size || tableEnd > originalSizeBytes) malformedMobi()
    val firstRecordOffset = prefix.readU32(PDB_RECORDS_OFFSET)
    if (firstRecordOffset < tableEnd || firstRecordOffset + PALMDOC_HEADER_BYTES > originalSizeBytes ||
        firstRecordOffset + MOBI_SIGNATURE_OFFSET + MOBI_FIXED_HEADER_BYTES > prefix.size) malformedMobi()

    val record = firstRecordOffset.toInt()
    val encryptionType = prefix.readU16(record + PALMDOC_ENCRYPTION_OFFSET)
    if (encryptionType != 0) throw PublicationParseException(
        PublicationFailureCode.PROTECTED_CONTENT,
        "Protected MOBI content is not supported.",
    )
    val compression = prefix.readU16(record + PALMDOC_COMPRESSION_OFFSET)
    if (compression !in SUPPORTED_PALMDOC_COMPRESSION) throw PublicationParseException(
        PublicationFailureCode.UNSUPPORTED_COMPRESSION,
        "The MOBI document uses an unsupported text compression.",
    )

    val mobi = record + MOBI_SIGNATURE_OFFSET
    if (!prefix.matchesAscii(mobi, MOBI_SIGNATURE)) malformedMobi()
    val headerLength = prefix.readU32(mobi + MOBI_HEADER_LENGTH_OFFSET)
    if (headerLength < MOBI_VERSION_FIELD_OFFSET + U32_BYTES ||
        headerLength > prefix.size.toLong() - mobi) malformedMobi()
    if (headerLength >= DRM_COUNT_OFFSET + U32_BYTES) {
        val drmOffset = prefix.readU32(mobi + DRM_OFFSET_FIELD)
        val drmCount = prefix.readU32(mobi + DRM_COUNT_OFFSET)
        if (drmOffset != UINT32_MAX && drmCount > 0 && drmCount != UINT32_MAX) protectedMobi()
    }
    val mobiType = prefix.readU32(mobi + MOBI_TYPE_OFFSET)
    val mobiVersion = prefix.readU32(mobi + MOBI_VERSION_FIELD_OFFSET)
    if (mobiType !in SUPPORTED_MOBI_TYPES) throw PublicationParseException(
        PublicationFailureCode.UNSUPPORTED_FORMAT,
        "The Palm database contains an unsupported MOBI content type.",
    )
    val features = readMobiFeatures(prefix, mobi, headerLength)
    if (features.fixedLayout) throw PublicationParseException(
        PublicationFailureCode.UNSUPPORTED_FIXED_LAYOUT,
        "Print Replica and other fixed-layout MOBI content are not supported.",
    )
    if (mobiVersion >= KF8_VERSION || mobiType == KF8_MOBI_TYPE || features.hasKf8Boundary) {
        return LocalPublicationFormat.AZW3
    }
    return LocalPublicationFormat.MOBI
}

private fun readMobiFeatures(prefix: ByteArray, mobiOffset: Int, mobiHeaderLength: Long): MobiFeatures {
    val exthOffsetLong = mobiOffset.toLong() + mobiHeaderLength
    if (exthOffsetLong + EXTH_HEADER_BYTES > prefix.size) return MobiFeatures()
    val exthOffset = exthOffsetLong.toInt()
    if (!prefix.matchesAscii(exthOffset, EXTH_SIGNATURE)) return MobiFeatures()
    val exthLength = prefix.readU32(exthOffset + EXTH_LENGTH_OFFSET)
    val recordCount = prefix.readU32(exthOffset + EXTH_RECORD_COUNT_OFFSET)
    if (exthLength < EXTH_HEADER_BYTES || exthLength > prefix.size.toLong() - exthOffset ||
        recordCount > MAX_EXTH_RECORDS) malformedMobi()
    var hasKf8Boundary = false
    var fixedLayout = false
    var cursor = exthOffset + EXTH_HEADER_BYTES
    val end = exthOffset + exthLength.toInt()
    repeat(recordCount.toInt()) {
        if (cursor + EXTH_RECORD_HEADER_BYTES > end) malformedMobi()
        val type = prefix.readU32(cursor)
        val recordLength = prefix.readU32(cursor + U32_BYTES)
        if (recordLength < EXTH_RECORD_HEADER_BYTES || recordLength > end - cursor) malformedMobi()
        val valueOffset = cursor + EXTH_RECORD_HEADER_BYTES
        val valueLength = recordLength.toInt() - EXTH_RECORD_HEADER_BYTES
        when (type) {
            KF8_BOUNDARY_EXTH_TYPE -> hasKf8Boundary = true
            FIXED_LAYOUT_EXTH_TYPE -> fixedLayout = prefix.asciiValue(valueOffset, valueLength).equals("true", true)
            BOOK_TYPE_EXTH_TYPE -> fixedLayout = fixedLayout || prefix.asciiValue(valueOffset, valueLength).equals("comic", true)
        }
        cursor += recordLength.toInt()
    }
    if (cursor > end) malformedMobi()
    return MobiFeatures(hasKf8Boundary, fixedLayout)
}

private fun ByteArray.asciiValue(offset: Int, length: Int): String {
    if (length < 0 || offset < 0 || offset > size - length) malformedMobi()
    return String(this, offset, length, StandardCharsets.US_ASCII).trim('\u0000', ' ', '\t', '\r', '\n')
}

private data class MobiFeatures(
    val hasKf8Boundary: Boolean = false,
    val fixedLayout: Boolean = false,
)

private fun readFormatPrefix(source: PublicationSource, cancelled: () -> Unit): ByteArray {
    val required = minOf(source.sizeBytes, MAX_DETECTOR_PREFIX_BYTES).toInt()
    val prefix = ByteArray(required)
    var count = 0
    try {
        source.openInputStream().use { input ->
            while (count < required) {
                cancelled()
                val read = input.read(prefix, count, required - count)
                when {
                    read < 0 -> throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL,
                        "The private original ended before its declared size.")
                    read == 0 -> {
                        val value = input.read()
                        if (value < 0) throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL,
                            "The private original ended before its declared size.")
                        prefix[count++] = value.toByte()
                    }
                    else -> count += read
                }
            }
        }
    } catch (error: PublicationParseException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL,
            "The private original could not be read for format detection.", error)
    } catch (error: Exception) {
        throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL,
            "The private original could not be read for format detection.", error)
    }
    return prefix
}

private fun looksLikeText(prefix: ByteArray): Boolean {
    if (prefix.isEmpty()) return false
    if (prefix.startsWithBytes(UTF8_BOM) || prefix.startsWithBytes(UTF16_LE_BOM) ||
        prefix.startsWithBytes(UTF16_BE_BOM) || prefix.startsWithBytes(UTF32_LE_BOM) || prefix.startsWithBytes(UTF32_BE_BOM)) {
        return true
    }
    val sampleLength = minOf(prefix.size, TEXT_SAMPLE_BYTES)
    var controls = 0
    for (index in 0 until sampleLength) {
        val value = prefix[index].toInt() and 0xff
        if (value == 0) return false
        if (value < 0x20 && value != '\t'.code && value != '\n'.code && value != '\r'.code && value != '\u000c'.code) {
            controls++
        }
    }
    return controls <= maxOf(2, sampleLength / 100)
}

private fun ByteArray.startsWithZipSignature(): Boolean = size >= 4 &&
    (matchesAscii(0, "PK\u0003\u0004") || matchesAscii(0, "PK\u0005\u0006") || matchesAscii(0, "PK\u0007\u0008"))

private fun ByteArray.startsWithUnsupportedContainer(): Boolean =
    matchesAscii(0, "KFX") || matchesAscii(0, "CONT") || matchesAscii(0, "DRMION") ||
        (size >= PDB_SIGNATURE_OFFSET + PDB_SIGNATURE.length && matchesAscii(PDB_TYPE_OFFSET, "BOOK") &&
            !matchesAscii(PDB_SIGNATURE_OFFSET, PDB_SIGNATURE))

private fun ByteArray.matchesAscii(offset: Int, value: String): Boolean {
    if (offset < 0 || offset > size - value.length) return false
    for (index in value.indices) if ((this[offset + index].toInt() and 0xff) != value[index].code) return false
    return true
}

private fun ByteArray.startsWithBytes(value: ByteArray): Boolean =
    size >= value.size && value.indices.all { this[it] == value[it] }

private fun ByteArray.readU16(offset: Int): Int {
    if (offset < 0 || offset > size - 2) malformedMobi()
    return ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)
}

private fun ByteArray.readU32(offset: Int): Long {
    if (offset < 0 || offset > size - U32_BYTES) malformedMobi()
    return ((this[offset].toLong() and 0xff) shl 24) or
        ((this[offset + 1].toLong() and 0xff) shl 16) or
        ((this[offset + 2].toLong() and 0xff) shl 8) or
        (this[offset + 3].toLong() and 0xff)
}

private fun malformedMobi(): Nothing = throw PublicationParseException(
    PublicationFailureCode.INVALID_ORIGINAL,
    "The Palm database MOBI header is malformed.",
)

private fun protectedMobi(): Nothing = throw PublicationParseException(
    PublicationFailureCode.PROTECTED_CONTENT,
    "Protected MOBI content is not supported.",
)

private const val MAX_DETECTOR_ORIGINAL_BYTES = 32L * 1024 * 1024
private const val MAX_DETECTOR_PREFIX_BYTES = 1024 * 1024L
private const val TEXT_SAMPLE_BYTES = 64 * 1024
private const val PDB_SIGNATURE_OFFSET = 60
private const val PDB_TYPE_OFFSET = 60
private const val PDB_HEADER_BYTES = 78
private const val PDB_RECORD_COUNT_OFFSET = 76
private const val PDB_RECORDS_OFFSET = 78
private const val PDB_RECORD_ENTRY_BYTES = 8
private const val PALMDOC_HEADER_BYTES = 16
private const val PALMDOC_COMPRESSION_OFFSET = 0
private const val PALMDOC_ENCRYPTION_OFFSET = 12
private const val MOBI_SIGNATURE_OFFSET = PALMDOC_HEADER_BYTES
private const val MOBI_FIXED_HEADER_BYTES = 0x6c
private const val MOBI_HEADER_LENGTH_OFFSET = 4
private const val MOBI_TYPE_OFFSET = 0x08
private const val MOBI_VERSION_FIELD_OFFSET = 0x14
private const val KF8_VERSION = 8L
private const val KF8_MOBI_TYPE = 248L
private const val EXTH_HEADER_BYTES = 12
private const val EXTH_LENGTH_OFFSET = 4
private const val EXTH_RECORD_COUNT_OFFSET = 8
private const val EXTH_RECORD_HEADER_BYTES = 8
private const val KF8_BOUNDARY_EXTH_TYPE = 121L
private const val FIXED_LAYOUT_EXTH_TYPE = 122L
private const val BOOK_TYPE_EXTH_TYPE = 123L
private const val MAX_EXTH_RECORDS = 4096L
// Record-0 DRM fields are at 0xa8/0xac; these offsets are relative to its MOBI header.
private const val DRM_OFFSET_FIELD = 0x98
private const val DRM_COUNT_OFFSET = 0x9c
private const val UINT32_MAX = 0xffff_ffffL
private const val U32_BYTES = 4
private val SHA256_REGEX = Regex("^[a-f0-9]{64}$")
private val PDB_SIGNATURE = "BOOKMOBI"
private val MOBI_SIGNATURE = "MOBI"
private val EXTH_SIGNATURE = "EXTH"
private val SUPPORTED_PALMDOC_COMPRESSION = setOf(1, 2, 17480)
private val UTF8_BOM = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
private val SUPPORTED_MOBI_TYPES = setOf(2L, 3L, 232L, KF8_MOBI_TYPE)
private val UTF16_LE_BOM = byteArrayOf(0xff.toByte(), 0xfe.toByte())
private val UTF16_BE_BOM = byteArrayOf(0xfe.toByte(), 0xff.toByte())
private val UTF32_LE_BOM = byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x00, 0x00)
private val UTF32_BE_BOM = byteArrayOf(0x00, 0x00, 0xfe.toByte(), 0xff.toByte())
