/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.bettermap;

/** The client has not finished downloading the part of the game cache a region needs; it is asked again later. */
class CacheNotReadyException extends RuntimeException
{
	CacheNotReadyException()
	{
		super(null, null, false, false);
	}
}
