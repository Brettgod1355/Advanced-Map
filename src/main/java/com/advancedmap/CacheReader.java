/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import net.runelite.api.Client;

/**
 * Reads the game cache the running client already has. The client's cache is only touched on the client thread,
 * so a read from a render thread waits in a queue that the client thread works through for a few milliseconds
 * each frame ({@link #service}), which keeps the game from stuttering while the map loads.
 */
class CacheReader
{
	/** How long the client thread spends on queued reads per frame. */
	private static final long BUDGET_NANOS = 4_000_000;

	interface Read<T>
	{
		T read(Client client);
	}

	static final class Pending<T>
	{
		final Read<T> work;
		final CompletableFuture<T> result = new CompletableFuture<>();

		Pending(Read<T> work)
		{
			this.work = work;
		}

		/**
		 * Runs the read for the thread waiting on it. Whatever it throws, the waiter is told, so it never waits for
		 * good; only an error of the virtual machine itself (out of memory) goes on to the client's frame.
		 */
		void run(Client client)
		{
			try
			{
				result.complete(work.read(client));
			}
			catch (Throwable t)
			{
				result.completeExceptionally(t);
				if (t instanceof VirtualMachineError)
				{
					throw (VirtualMachineError) t;
				}
			}
		}
	}

	private final Client client;
	private final Queue<Pending<?>> queue = new ConcurrentLinkedQueue<>();
	private volatile boolean open = true;

	CacheReader(Client client)
	{
		this.client = client;
	}

	/**
	 * Runs {@code work} on the client thread and returns its result, waiting for it when called from another
	 * thread. Throws {@link CancellationException} once the plugin has stopped.
	 */
	<T> T read(Read<T> work)
	{
		if (client.isClientThread())
		{
			return work.read(client);
		}
		if (!open)
		{
			throw new CancellationException("Advanced Map stopped");
		}
		Pending<T> pending = new Pending<>(work);
		queue.add(pending);
		if (!open)
		{
			pending.result.cancel(false);
		}
		try
		{
			return pending.result.get();
		}
		catch (InterruptedException e)
		{
			throw new CancellationException("Advanced Map stopped");
		}
		catch (ExecutionException e)
		{
			Throwable cause = e.getCause();
			throw cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(cause);
		}
	}

	/** Works through queued reads for a few milliseconds. Client thread, once a frame. */
	void service()
	{
		long until = System.nanoTime() + BUDGET_NANOS;
		Pending<?> pending;
		while (System.nanoTime() < until && (pending = queue.poll()) != null)
		{
			pending.run(client);
		}
	}

	/** Stops serving: queued and future reads are cancelled. */
	void close()
	{
		open = false;
		Pending<?> pending;
		while ((pending = queue.poll()) != null)
		{
			pending.result.cancel(false);
		}
	}
}
