/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android.publication

import java.io.IOException

/** Stable raw failure returned by the isolated publication transport. */
class MobiPublicationTransportException(
    val code: String,
    cause: Throwable? = null,
) : IOException(code, cause)
