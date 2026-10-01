package com.yscanner.detection.model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lifecycle state of a [SegmentationModel].
 */
enum class ModelState {
    /** Weights not loaded. No native resources held. */
    UNLOADED,

    /** Load in progress. */
    LOADING,

    /** Ready to serve [SegmentationModel.infer]. */
    READY,

    /** Load or inference failed unrecoverably. See the model's failure cause. */
    FAILED,

    /** Released. Cannot be used again. */
    CLOSED
}

/**
 * Thrown when a model cannot be prepared for inference.
 *
 * Callers must treat this as a *degradation* signal, never as a fatal error:
 * per the project's error-handling rules the scanner must remain usable, so the
 * pipeline falls back to the classical detector and manual capture.
 */
class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A replaceable on-device document segmentation model.
 *
 * This is the seam the whole detection pipeline is built around. The rest of
 * yScanner knows only this interface — never a concrete runtime — so the model
 * can be swapped, A/B tested, or downgraded without touching the pipeline.
 *
 * Contract:
 *  - [infer] must be safe to call from a background dispatcher and must not
 *    block the main thread. It is declared `suspend` so implementations can
 *    move work to an I/O or default dispatcher and remain cancellable.
 *  - [infer] must never throw on malformed *input data*; return a result with
 *    `documentPresent = false` instead. It may throw only on genuine runtime
 *    failure, which the caller treats as a degradation.
 *  - Implementations must be safe to [close] more than once.
 *  - [descriptor] must be available *before* [load], so the pipeline can size
 *    its buffers without paying the load cost.
 */
interface SegmentationModel : AutoCloseable {

    /** Static model metadata. Valid in every state, including [ModelState.UNLOADED]. */
    val descriptor: ModelDescriptor

    /** Current lifecycle state. */
    val state: ModelState

    /** Last failure cause when [state] is [ModelState.FAILED], else null. */
    val failureCause: Throwable?

    /**
     * Loads weights and prepares the runtime. Idempotent: calling while already
     * [ModelState.READY] is a no-op. Calling after [close] throws.
     */
    suspend fun load()

    /**
     * Runs inference on a prepared input.
     *
     * @throws IllegalStateException if called before [load] or after [close].
     * @throws ModelLoadException if the runtime fails; callers degrade gracefully.
     */
    suspend fun infer(input: ModelInput): SegmentationOutput

    /** Releases native resources. Safe to call repeatedly. */
    override fun close()
}

/**
 * Owns the lifecycle of a single [SegmentationModel] for the duration of a
 * camera session.
 *
 * Rationale (PRD memory rules): the model must be loaded **once per session**,
 * not per frame, and released on exit. Loading is expensive and repeated
 * load/release cycles fragment native memory. [ModelManager] centralises that
 * decision so no call site has to get it right independently.
 *
 * Thread-safe: concurrent [ensureLoaded] calls collapse into a single load.
 */
class ModelManager(
    private val factory: () -> SegmentationModel
) {
    private val mutex = Mutex()
    private var model: SegmentationModel? = null

    /** The underlying model, or null before the first [ensureLoaded]. */
    val current: SegmentationModel? get() = model

    /** Descriptor of the model this manager will produce, without loading it. */
    val descriptor: ModelDescriptor by lazy { factory().also { it.close() }.descriptor }

    /**
     * Returns a loaded, ready model, loading it on first use.
     *
     * @throws ModelLoadException if the model failed to load. The manager stays
     *   usable: a later call retries, because transient delegate failures are
     *   common on mid-range devices.
     */
    suspend fun ensureLoaded(): SegmentationModel = mutex.withLock {
        val existing = model
        if (existing != null) {
            when (existing.state) {
                ModelState.READY -> return existing
                ModelState.FAILED -> {
                    // Release the broken instance and retry from scratch.
                    existing.close()
                    model = null
                }
                ModelState.CLOSED -> {
                    model = null
                }
                ModelState.UNLOADED, ModelState.LOADING -> {
                    // Another coroutine is mid-load; fall through and load fresh.
                }
            }
        }
        val created = model ?: factory().also { model = it }
        created.load()
        created
    }

    /**
     * Releases the model. Safe to call when nothing is loaded, and safe to call
     * twice. After this the manager may be used again (it will reload).
     */
    suspend fun release() = mutex.withLock {
        model?.close()
        model = null
    }
}
