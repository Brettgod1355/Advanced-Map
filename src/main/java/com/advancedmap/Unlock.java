/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.ObjectDefinition;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import lombok.Value;

/**
 * An object the game switches with one of the player's variables: it stands for another object for each value, as
 * a bank chest space becomes a bank chest once the player builds it, or shelves become a hole as The Lost Tribe goes
 * on. Which values make it usable comes from the game's data; the value itself is the player's, read while logged in.
 */
@Value
class Unlock
{
	/** The variable: a varbit when 0 or more, else the varp {@code -var - 1}. */
	int variable;
	/** Usable at each value; the last entry stands for every value past the others, as the game reads it. */
	boolean[] open;

	/** True when the object is usable at a value of its variable; never at an unknown (negative) one. */
	boolean isOpen(int value)
	{
		if (value < 0)
		{
			return false;
		}
		return open[Math.min(value, open.length - 1)];
	}

	/**
	 * True when every value the variable takes leaves the object usable, so nothing needs unlocking (the Grand
	 * Exchange's booths). The last entry is left out: the game's variables stay within the listed values.
	 */
	boolean always()
	{
		for (int i = 0; i < open.length - 1; i++)
		{
			if (!open[i])
			{
				return false;
			}
		}
		return open.length > 1;
	}

	/**
	 * The unlock of an object that stands for others, when at least one of them is {@code usable}; null for an
	 * object that stands for no other or never for a usable one.
	 */
	static Unlock of(ObjectDefinition object, IntFunction<ObjectDefinition> objects, Predicate<ObjectDefinition> usable)
	{
		int[] states = object.getConfigChangeDest();
		int var = object.getVarbitID() != -1 ? object.getVarbitID()
			: object.getVarpID() != -1 ? -object.getVarpID() - 1 : Integer.MIN_VALUE;
		if (states == null || states.length == 0 || var == Integer.MIN_VALUE)
		{
			return null;
		}
		boolean[] open = new boolean[states.length];
		boolean any = false;
		for (int i = 0; i < states.length; i++)
		{
			open[i] = states[i] != -1 && usable.test(objects.apply(states[i]));
			any |= open[i];
		}
		return any ? new Unlock(var, open) : null;
	}
}
