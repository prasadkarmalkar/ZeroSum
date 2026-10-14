package com.zerosum.zerosum.concurrency;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs tasks so they all start at the same instant, then collects every result and every exception.
 * <p>
 * Each task gets its own thread. All threads first report ready, then wait on one shared start signal,
 * so none gets a head start while the others are still being created.
 */
public final class ConcurrentRunner {

	/** What one task produced: a value, or the exception it threw (never both). */
	public record Outcome<T>(T value, Throwable error) {

		public boolean succeeded() {
			return error == null;
		}

	}

	private ConcurrentRunner() {
	}

	/**
	 * @throws AssertionError if the tasks don't all finish within the timeout (a likely deadlock or hang)
	 */
	public static <T> List<Outcome<T>> runAllAtOnce(List<Callable<T>> tasks, Duration timeout) throws InterruptedException {
		ExecutorService threads = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(threads.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			if (!ready.await(30, TimeUnit.SECONDS)) {
				throw new AssertionError("Not all " + tasks.size() + " threads became ready");
			}
			start.countDown();

			long deadline = System.nanoTime() + timeout.toNanos();
			List<Outcome<T>> outcomes = new ArrayList<>();
			for (Future<T> future : futures) {
				long remaining = Math.max(0, deadline - System.nanoTime());
				try {
					outcomes.add(new Outcome<>(future.get(remaining, TimeUnit.NANOSECONDS), null));
				} catch (ExecutionException e) {
					outcomes.add(new Outcome<>(null, e.getCause()));
				} catch (TimeoutException e) {
					throw new AssertionError("Tasks did not all finish within " + timeout + " (deadlock or hang?)", e);
				}
			}
			return outcomes;
		} finally {
			threads.shutdownNow();
		}
	}

}
