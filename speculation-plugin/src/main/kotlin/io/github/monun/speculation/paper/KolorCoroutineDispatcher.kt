package io.github.monun.speculation.paper

import io.github.Shai0717.kolor.KolorScheduler
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.resume
import kotlin.coroutines.CoroutineContext

@OptIn(InternalCoroutinesApi::class)
class KolorCoroutineDispatcher(private val scheduler: KolorScheduler) : CoroutineDispatcher(), Delay {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        scheduler.runGlobal { block.run() }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val task = scheduler.runGlobal(delayTicks(timeMillis)) {
            continuation.resume(Unit)
        }
        continuation.invokeOnCancellation { task.cancel() }
    }

    override fun invokeOnTimeout(
        timeMillis: Long,
        block: Runnable,
        context: CoroutineContext
    ): DisposableHandle {
        val task = scheduler.runGlobal(delayTicks(timeMillis)) { block.run() }
        return DisposableHandle { task.cancel() }
    }

    fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + this)

    private fun delayTicks(timeMillis: Long): Long =
        if (timeMillis <= 0L) 0L else 1L + (timeMillis - 1L) / MILLIS_PER_TICK

    private companion object {
        const val MILLIS_PER_TICK = 50L
    }
}
