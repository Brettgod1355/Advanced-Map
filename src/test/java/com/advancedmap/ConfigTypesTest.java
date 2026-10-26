/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * RuneLite reads settings through a proxy of the config interface, and a proxy cannot reach a type that is not
 * public: a setting returning a package-private enum stopped the plugin from starting (2026-10-26). Test code only:
 * the plugin itself uses no reflection.
 */
public class ConfigTypesTest
{
	@Test
	public void everySettingsTypeOfOursIsPublic()
	{
		for (Method method : AdvancedMapConfig.class.getMethods())
		{
			Class<?> type = method.getReturnType();
			if (type.getPackage() != null && type.getPackage().getName().startsWith("com.advancedmap"))
			{
				assertTrue(method.getName() + " returns " + type.getSimpleName() + ", which must be public",
					Modifier.isPublic(type.getModifiers()));
			}
		}
	}
}
