package com.localscan.camera.executor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CameraExecutorTest {

    @Test
    fun `tasks execute on background daemon thread with expected naming prefix`() {
        val executor = CameraExecutor()
        val threadNameRef = AtomicReference<String>()
        val isDaemonRef = AtomicReference<Boolean>()
        val latch = CountDownLatch(1)

        executor.execute {
            threadNameRef.set(Thread.currentThread().name)
            isDaemonRef.set(Thread.currentThread().isDaemon)
            latch.countDown()
        }

        val completed = latch.await(2, TimeUnit.SECONDS)
        assertThat(completed).isTrue()
        assertThat(threadNameRef.get()).startsWith("yScanner-AnalysisThread-")
        assertThat(isDaemonRef.get()).isTrue()

        executor.shutdown()
        assertThat(executor.isShutdown).isTrue()
    }

    @Test
    fun `shutdown terminates executor cleanly`() {
        val executor = CameraExecutor()
        assertThat(executor.isShutdown).isFalse()

        executor.shutdown()
        assertThat(executor.isShutdown).isTrue()
    }
}
