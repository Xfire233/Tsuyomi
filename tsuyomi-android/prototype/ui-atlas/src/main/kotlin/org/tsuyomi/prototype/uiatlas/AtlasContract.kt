/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

/**
 * Determinism anchors for the whole atlas (Atlas Spec §1.5, §4). Every fixture, cover, and
 * dynamic-color derivation draws from these constants; the atlas never reads a wall clock,
 * an unseeded random source, the network, a database, or a file outside its own process.
 */
const val ATLAS_SEED: Long = 20260811L

/** Fixed review clock rendered wherever a timestamp is shown. */
const val ATLAS_FIXED_CLOCK: String = "2026-08-11T09:30:00+08:00"
