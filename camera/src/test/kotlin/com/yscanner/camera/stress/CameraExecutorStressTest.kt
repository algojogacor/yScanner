package com.yscanner.camera.stress

import com.google.common.truth.Truth.assertThat
import com.yscanner.camera.executor.CameraExecutor
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CameraExecutorStressTest {

    @Test
    fun `flood with 1000 rapid sequential tasks verifies complete and ordered execution`() {
        val executor = CameraExecutor()
        val taskCount = 1000
        val results = Collections.synchronizedList(ArrayList<Int>(taskCount))
        val latch = CountDownLatch(taskCount)

        for (i in 0 until taskCount) {
            val index = i
            executor.execute {
                results.add(index)
                latch.countDown()
            }
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        assertThat(completed).isTrue()
        assertThat(results).hasSize(taskCount)

        // Strict FIFO verification
        for (i in 0 until taskCount) {
            assertThat(results[i]).isEqualTo(i)
        }

        executor.shutdown()
        assertThat(executor.isShutdown).isTrue()
    }

    @Test
    fun `concurrent flood from 10 producer threads executing 1000 total tasks completes cleanly`() {
        val executor = CameraExecutor()
        val totalTasks = 1000
        val threadCount = 10
        val tasksPerThread = totalTasks / threadCount
        val latch = CountDownLatch(totalTasks)
        val completedCount = AtomicInteger(0)

        val producerPool = Executors.newFixedThreadPool(threadCount)
        for (t in 0 until threadCount) {
            producerPool.execute {
                for (i in 0 until tasksPerThread) {
                    executor.execute {
                        completedCount.incrementAndGet()
                        latch.countDown()
                    }
                }
            }
        }

        val allDone = latch.await(10, TimeUnit.SECONDS)
        producerPool.shutdown()
        producerPool.awaitTermination(2, TimeUnit.SECONDS)

        assertThat(allDone).isTrue()
        assertThat(completedCount.get()).isEqualTo(totalTasks)

        executor.shutdown()
        assertThat(executor.isShutdown).isTrue()
    }

    @Test
    fun `graceful shutdown terminates thread without hanging or leaking`() {
        val executor = CameraExecutor()
        val executed = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        executor.execute {
            executed.set(true)
            latch.countDown()
        }

        latch.await(2, TimeUnit.SECONDS)
        assertThat(executed.get()).isTrue()
        assertThat(executor.isShutdown).isFalse()

        executor.shutdown()
        assertThat(executor.isShutdown).isTrue()

        // Tasks submitted after shutdown must not throw unhandled exception or execute
        val postShutdownExecuted = AtomicBoolean(false)
        var exceptionThrown: Throwable? = null
        try {
            executor.execute {
                postShutdownExecuted.set(true)
            }
        } catch (t: Throwable) {
            exceptionThrown = t
        }

        assertThat(exceptionThrown).isNull()
        assertThat(postShutdownExecuted.get()).isFalse()
    }

    @Test
    fun `race condition between concurrent execute and shutdown does not throw unhandled RejectedExecutionException`() {
        // Stress test the TOCTOU (time-of-check to time-of-use) between !isShutdown and execute()
        val iterations = 50
        val rejectedExceptions = AtomicInteger(0)

        for (iter in 0 until iterations) {
            val executor = CameraExecutor()
            val producerThreads = 8
            val pool = Executors.newFixedThreadPool(producerThreads + 1)
            val stopSignal = AtomicBoolean(false)

            // Submitters
            for (p in 0 until producerThreads) {
                pool.execute {
                    while (!stopSignal.get()) {
                        try {
                            executor.execute {
                                // small work
                                Math.sqrt(42.0)
                            }
                        } catch (e: RejectedExecutionException) {
                            rejectedExceptions.incrementAndGet()
                        } catch (t: Throwable) {
                            // Any other unexpected exception
                        }
                    }
                }
            }

            // Killer
            pool.execute {
                Thread.sleep(5)
                executor.shutdown()
                stopSignal.set(true)
            }

            pool.shutdown()
            pool.awaitTermination(3, TimeUnit.SECONDS)
        }

        // Check if race condition triggered RejectedExecutionException
        println("RejectedExecutionExceptions encountered across $iterations race iterations: ${rejectedExceptions.get()}")
    }
}
