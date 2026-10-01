package com.yscanner.camera.stress

import com.google.common.truth.Truth.assertThat
import com.yscanner.camera.CameraXControllerImpl
import com.yscanner.domain.model.FlashMode
import com.yscanner.domain.model.PointF
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CameraStateMachineStressTest {

    @Test
    fun `rapid flash mode toggles across concurrent threads preserve state integrity without crashing`() {
        val controller = CameraXControllerImpl()
        val threadCount = 10
        val togglesPerThread = 100
        val pool = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val modes = listOf(FlashMode.OFF, FlashMode.ON, FlashMode.TORCH)

        for (t in 0 until threadCount) {
            pool.execute {
                try {
                    for (i in 0 until togglesPerThread) {
                        val mode = modes[i % modes.size]
                        controller.setFlashMode(mode)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        pool.shutdown()

        assertThat(completed).isTrue()
        assertThat(controller.flashMode.value).isIn(modes)
    }

    @Test
    fun `focusOnPoint when camera is not initialized safely returns without throwing exception`() {
        val controller = CameraXControllerImpl()
        // Controller is uninitialized (camera == null, previewView == null)
        var exception: Throwable? = null
        try {
            controller.focusOnPoint(PointF(0.5f, 0.5f))
        } catch (t: Throwable) {
            exception = t
        }

        assertThat(exception).isNull()
    }

    @Test
    fun `concurrent focusOnPoint calls execute safely without deadlock or exception`() {
        val controller = CameraXControllerImpl()
        val threadCount = 16
        val callsPerThread = 50
        val pool = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            pool.execute {
                try {
                    for (i in 0 until callsPerThread) {
                        val x = (i % 10) / 10.0f
                        val y = ((i * 3) % 10) / 10.0f
                        controller.focusOnPoint(PointF(x, y))
                    }
                } catch (t: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        pool.shutdown()

        assertThat(completed).isTrue()
        assertThat(errorCount.get()).isEqualTo(0)
    }
}
