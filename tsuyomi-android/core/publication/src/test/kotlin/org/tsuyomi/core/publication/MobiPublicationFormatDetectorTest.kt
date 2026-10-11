/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat

class MobiPublicationFormatDetectorTest {
    @Test
    fun detectsByMagicAndHeaderRatherThanName() {
        assertEquals(LocalPublicationFormat.EPUB, detectPublicationFormat(source("PK\u0003\u0004".toByteArray())))
        assertEquals(LocalPublicationFormat.MOBI, detectPublicationFormat(source(pdbMobi(version = 6))))
        assertEquals(LocalPublicationFormat.AZW3, detectPublicationFormat(source(pdbMobi(version = 8))))
        assertEquals(LocalPublicationFormat.AZW3,
            detectPublicationFormat(source(pdbMobi(version = 6, exth = listOf(121L to byteArrayOf(0, 0, 0, 1))))))
        assertEquals(LocalPublicationFormat.TXT, detectPublicationFormat(source("\uFEFFBook text".toByteArray())))
    }

    @Test
    fun rejectsProtectedFixedLayoutKfxAndMalformedBinaryInputsExplicitly() {
        assertFailure(PublicationFailureCode.PROTECTED_CONTENT) {
            detectPublicationFormat(source(pdbMobi(version = 6, encryption = 2)))
        }
        assertFailure(PublicationFailureCode.UNSUPPORTED_FIXED_LAYOUT) {
            detectPublicationFormat(source(pdbMobi(version = 6, exth = listOf(122L to "true".toByteArray()))))
        }
        assertFailure(PublicationFailureCode.UNSUPPORTED_FORMAT) {
            detectPublicationFormat(source("CONT\u0000KFX".toByteArray()))
        }
        assertFailure(PublicationFailureCode.UNSUPPORTED_FORMAT) {
            detectPublicationFormat(source(byteArrayOf(0, 0, 1, 2, 3, 0, 4)))
        }
        assertFailure(PublicationFailureCode.INVALID_ORIGINAL) {
            detectPublicationFormat(source(pdbMobi(version = 6).copyOf(90)))
        }
    }

    private fun assertFailure(code: PublicationFailureCode, action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected publication failure $code")
        } catch (error: PublicationParseException) {
            assertEquals(code, error.code)
        }
    }

    private fun source(bytes: ByteArray): PublicationSource = object : PublicationSource {
        override val sha256: String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        override val sizeBytes: Long = bytes.size.toLong()
        override fun openInputStream(): InputStream = ByteArrayInputStream(bytes)
        override fun openArchive(): PublicationZipArchive = error("Format detection does not open archives")
    }

    private fun pdbMobi(
        version: Int,
        encryption: Int = 0,
        exth: List<Pair<Long, ByteArray>> = emptyList(),
    ): ByteArray {
        val firstRecord = 86
        val mobi = firstRecord + 16
        val headerLength = 0xe4
        val exthBytes = if (exth.isEmpty()) ByteArray(0) else {
            val length = 12 + exth.sumOf { 8 + it.second.size }
            ByteArray(length).also { output ->
                output.putAscii(0, "EXTH")
                output.putU32(4, length.toLong())
                output.putU32(8, exth.size.toLong())
                var cursor = 12
                for ((type, value) in exth) {
                    output.putU32(cursor, type)
                    output.putU32(cursor + 4, (8 + value.size).toLong())
                    value.copyInto(output, cursor + 8)
                    cursor += 8 + value.size
                }
            }
        }
        return ByteArray(mobi + headerLength + exthBytes.size).also { output ->
            output.putAscii(60, "BOOKMOBI")
            output.putU16(76, 1)
            output.putU32(78, firstRecord.toLong())
            output.putU16(firstRecord, 2)
            output.putU16(firstRecord + 12, encryption)
            output.putAscii(mobi, "MOBI")
            output.putU32(mobi + 4, headerLength.toLong())
            output.putU32(mobi + 0x08, if (version >= 8) 248 else 2)
            output.putU32(mobi + 0x14, version.toLong())
            output.putU32(mobi + 0x18, 0xffff_ffffL)
            output.putU32(mobi + 0x24, 0xffff_ffffL)
            exthBytes.copyInto(output, mobi + headerLength)
        }
    }

    private fun ByteArray.putAscii(offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(this, offset)
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Long) {
        for (index in 0 until 4) this[offset + index] = (value ushr (24 - index * 8)).toByte()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
