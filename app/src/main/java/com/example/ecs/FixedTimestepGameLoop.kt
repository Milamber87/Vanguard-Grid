package com.example.ecs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Coroutine-driven fixed-timestep game loop that executes ECS simulation ticks
 * completely independently of the Jetpack Compose UI frame rate.
 *
 * Uses a monotonic clock accumulator with spiral-of-death clamping so logic always
 * advances in deterministic [fixedStepSeconds] increments regardless of device refresh rate
 * (60Hz, 90Hz, 120Hz) or UI rendering jank.
 */
class FixedTimestepGameLoop(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val targetHz: Int = EcsPlanetRuntime.FIXED_HZ,
    private val maxFrameDeltaSeconds: Float = EcsPlanetRuntime.MAX_FRAME_DELTA_SECONDS,
    private val maxCatchUpSteps: Int = EcsPlanetRuntime.MAX_STEPS_PER_FRAME,
    private val nanoTimeProvider: () -> Long = { System.nanoTime() },
    private val onFixedTick: suspend (dtSeconds: Float) -> Unit,
    private val onBatchComplete: suspend (stepsExecuted: Int, measuredHz: Int) -> Unit = { _, _ -> }
) {
    val fixedStepSeconds: Float = 1f / targetHz.coerceAtLeast(1)
    private val fixedStepNanos: Long = (1_000_000_000L / targetHz.coerceAtLeast(1))

    @Volatile
    var paused: Boolean = false
        private set

    @Volatile
    var timeScale: Float = 1f
        private set

    private var loopJob: Job? = null

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(dispatcher) {
            var lastTimeNanos = nanoTimeProvider()
            var accumulatorNanos = 0L
            var ticksInWindow = 0
            var windowStartNanos = lastTimeNanos
            var measuredHz = targetHz

            while (isActive) {
                val nowNanos = nanoTimeProvider()
                val rawElapsedNanos = max(0L, nowNanos - lastTimeNanos)
                lastTimeNanos = nowNanos

                if (paused || timeScale <= 0f) {
                    accumulatorNanos = 0L
                    windowStartNanos = nowNanos
                    ticksInWindow = 0
                    delay(PAUSED_POLL_MS)
                    continue
                }

                val maxDeltaNanos = (maxFrameDeltaSeconds * 1_000_000_000L).toLong()
                val clampedNanos = rawElapsedNanos.coerceAtMost(maxDeltaNanos)
                accumulatorNanos += (clampedNanos * timeScale).toLong()

                var steps = 0
                while (accumulatorNanos >= fixedStepNanos && steps < maxCatchUpSteps) {
                    onFixedTick(fixedStepSeconds)
                    accumulatorNanos -= fixedStepNanos
                    steps++
                    ticksInWindow++
                }

                // If catch-up cap was hit, discard excess backlog to prevent spiral of death
                if (steps == maxCatchUpSteps && accumulatorNanos > fixedStepNanos) {
                    accumulatorNanos = 0L
                }

                // Measure rolling actual simulation Hz once per second
                val windowElapsedNanos = nowNanos - windowStartNanos
                if (windowElapsedNanos >= 1_000_000_000L) {
                    measuredHz = ((ticksInWindow * 1_000_000_000L) / windowElapsedNanos).toInt()
                    ticksInWindow = 0
                    windowStartNanos = nowNanos
                }

                if (steps > 0) {
                    onBatchComplete(steps, measuredHz)
                }

                // Sleep cleanly until the next fixed timestep boundary
                val remainingNanos = (fixedStepNanos - accumulatorNanos).coerceAtLeast(1_000_000L)
                val sleepMs = max(1L, (remainingNanos / 1_000_000L / timeScale).toLong())
                delay(sleepMs)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    fun setPaused(shouldPause: Boolean) {
        paused = shouldPause
    }

    fun setTimeScale(scale: Float) {
        timeScale = scale.coerceIn(0.25f, 4f)
    }

    fun isRunning(): Boolean = loopJob?.isActive == true

    companion object {
        private const val PAUSED_POLL_MS = 50L
    }
}
