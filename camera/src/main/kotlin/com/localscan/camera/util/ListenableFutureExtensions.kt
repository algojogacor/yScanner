package com.localscan.camera.util

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits completion of a [ListenableFuture] in a coroutine without blocking threads.
 */
suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    val directExecutor = Executor { command -> command.run() }

    addListener({
        if (continuation.isCancelled) return@addListener
        try {
            continuation.resume(get())
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            continuation.resumeWithException(cause)
        } catch (e: CancellationException) {
            continuation.cancel(e)
        } catch (e: Throwable) {
            continuation.resumeWithException(e)
        }
    }, directExecutor)

    continuation.invokeOnCancellation {
        cancel(true)
    }
}
