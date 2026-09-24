package cn.limpu.hita.data.work

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSelectionServiceStartQueueTest {
    @Test
    fun `handler exception does not prevent later queued start from finishing`() {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val ignoredExpectedException = CoroutineExceptionHandler { _, _ -> }
        val scope = CoroutineScope(SupervisorJob() + dispatcher + ignoredExpectedException)
        val allFinished = CompletableDeferred<Unit>()
        val handled = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<Int>()
        try {
            val queue = CourseSelectionServiceStartQueue(
                scope = scope,
                handle = { start ->
                    handled += start.startId
                    if (start.startId == 1) error("expected handler failure")
                },
                onFinished = { startId ->
                    finished += startId
                    if (finished.size == 2) allFinished.complete(Unit)
                }
            )

            queue.enqueue(CourseSelectionServiceStart(startId = 1, jobId = "failing"))
            queue.enqueue(CourseSelectionServiceStart(startId = 2, jobId = "later"))

            kotlinx.coroutines.runBlocking {
                withTimeout(5_000L) { allFinished.await() }
            }
            assertEquals(listOf(1, 2), handled.toList())
            assertEquals(listOf(1, 2), finished.toList())
        } finally {
            scope.cancel()
            dispatcher.close()
        }
    }

    @Test
    fun `newer ready start cannot overtake or stop older work`() {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val allFinished = CompletableDeferred<Unit>()
        val handled = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<Int>()
        try {
            val queue = CourseSelectionServiceStartQueue(
                scope = scope,
                handle = { start ->
                    handled += start.startId
                    if (start.startId == 1) {
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    }
                },
                onFinished = { startId ->
                    finished += startId
                    if (finished.size == 2) allFinished.complete(Unit)
                }
            )

            queue.enqueue(CourseSelectionServiceStart(startId = 1, jobId = "older"))
            queue.enqueue(CourseSelectionServiceStart(startId = 2, jobId = "newer"))
            val dispatcherProbe = scope.async { Unit }

            kotlinx.coroutines.runBlocking {
                withTimeout(5_000L) {
                    firstEntered.await()
                    dispatcherProbe.await()
                }
                assertEquals(listOf(1), handled.toList())
                assertTrue(finished.isEmpty())

                releaseFirst.complete(Unit)
                withTimeout(5_000L) { allFinished.await() }
                assertEquals(listOf(1, 2), handled.toList())
                assertEquals(listOf(1, 2), finished.toList())
            }
        } finally {
            scope.cancel()
            dispatcher.close()
        }
    }
}
