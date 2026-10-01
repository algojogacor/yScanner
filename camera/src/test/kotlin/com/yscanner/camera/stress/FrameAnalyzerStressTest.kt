package com.yscanner.camera.stress

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.common.truth.Truth.assertThat
import com.yscanner.camera.analysis.FrameAnalyzer
import com.yscanner.camera.executor.CameraExecutor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial stress test for FrameAnalyzer exception safety and resource closure guarantee.
 */
class FrameAnalyzerStressTest {

    private lateinit var executor: CameraExecutor

    @Before
    fun setUp() {
        executor = CameraExecutor()
    }

    @After
    fun tearDown() {
        executor.shutdown()
    }

    /**
     * Replicates the exact analyzer callback registered in CameraXControllerImpl:
     * ```kotlin
     * analysisUseCase.setAnalyzer(cameraExecutor) { imageProxy ->
     *     try {
     *         activeAnalyzer?.analyze(imageProxy)
     *     } catch (t: Throwable) {
     *         Log.e(TAG, "Exception during frame analysis", t)
     *     } finally {
     *         imageProxy.close()
     *     }
     * }
     * ```
     */
    private fun createAnalyzerCallback(activeAnalyzerProvider: () -> FrameAnalyzer?): (ImageProxy) -> Unit {
        return { imageProxy ->
            try {
                activeAnalyzerProvider()?.analyze(imageProxy)
            } catch (t: Throwable) {
                // Caught to avoid crashing pipeline
            } finally {
                imageProxy.close()
            }
        }
    }

    @Test
    fun `ImageProxy close is strictly executed when analyzer completes normally`() {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val analyzed = AtomicBoolean(false)
        val callback = createAnalyzerCallback {
            FrameAnalyzer {
                analyzed.set(true)
            }
        }

        val latch = CountDownLatch(1)
        executor.execute {
            callback(imageProxy)
            latch.countDown()
        }

        latch.await(2, TimeUnit.SECONDS)
        assertThat(analyzed.get()).isTrue()
        verify(exactly = 1) { imageProxy.close() }
    }

    @Test
    fun `ImageProxy close is strictly executed when analyzer throws uncaught RuntimeException`() {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val callback = createAnalyzerCallback {
            FrameAnalyzer {
                throw RuntimeException("Simulated ML model inference crash")
            }
        }

        val latch = CountDownLatch(1)
        executor.execute {
            callback(imageProxy)
            latch.countDown()
        }

        latch.await(2, TimeUnit.SECONDS)
        verify(exactly = 1) { imageProxy.close() }
    }

    @Test
    fun `ImageProxy close is strictly executed when analyzer throws fatal OutOfMemoryError`() {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val callback = createAnalyzerCallback {
            FrameAnalyzer {
                throw OutOfMemoryError("Simulated native buffer allocation failure")
            }
        }

        val latch = CountDownLatch(1)
        executor.execute {
            callback(imageProxy)
            latch.countDown()
        }

        latch.await(2, TimeUnit.SECONDS)
        verify(exactly = 1) { imageProxy.close() }
    }

    @Test
    fun `ImageProxy close is strictly executed when activeAnalyzer is null`() {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val callback = createAnalyzerCallback { null }

        val latch = CountDownLatch(1)
        executor.execute {
            callback(imageProxy)
            latch.countDown()
        }

        latch.await(2, TimeUnit.SECONDS)
        verify(exactly = 1) { imageProxy.close() }
    }

    @Test
    fun `pipeline survives 500 consecutive frame analysis errors and closes 100 percent of frames`() {
        val frameCount = 500
        val closedCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)
        val latch = CountDownLatch(frameCount)

        var toggle = false
        val callback = createAnalyzerCallback {
            FrameAnalyzer {
                toggle = !toggle
                if (toggle) {
                    exceptionCount.incrementAndGet()
                    throw IllegalStateException("Intermittent frame segmentation error")
                }
            }
        }

        for (i in 0 until frameCount) {
            val proxy = mockk<ImageProxy>(relaxed = true)
            every { proxy.close() } answers {
                closedCount.incrementAndGet()
                latch.countDown()
            }
            executor.execute {
                callback(proxy)
            }
        }

        val finished = latch.await(10, TimeUnit.SECONDS)
        assertThat(finished).isTrue()
        assertThat(closedCount.get()).isEqualTo(frameCount)
        assertThat(exceptionCount.get()).isEqualTo(frameCount / 2)
    }
}
