package com.yscanner.camera.executor

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dedicated single-threaded executor for camera frame analysis.
 *
 * Ensures:
 * 1. Analysis never runs on the main thread (zero UI stutter).
 * 2. Predictable, sequential execution without thread contention.
 * 3. Named daemon threads for easy profiling and clean lifecycle teardown.
 */
class CameraExecutor : Executor {

    private val threadNumber = AtomicInteger(1)
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor(
        ThreadFactory { runnable ->
            Thread(runnable, "yScanner-AnalysisThread-${threadNumber.getAndIncrement()}").apply {
                priority = Thread.NORM_PRIORITY
                isDaemon = true
            }
        }
    )

    override fun execute(command: Runnable) {
        if (!executorService.isShutdown) {
            executorService.execute(command)
        }
    }

    /**
     * Gracefully shuts down executor and cleans up active threads.
     */
    fun shutdown() {
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(250, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    val isShutdown: Boolean
        get() = executorService.isShutdown
}
