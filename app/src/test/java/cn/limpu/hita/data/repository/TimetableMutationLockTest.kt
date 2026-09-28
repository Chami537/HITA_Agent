package cn.limpu.hita.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TimetableMutationLockTest {
    @Test
    fun adoptSeesTheDecisionOnlyAfterRefreshReleasesIt() {
        val lock = TimetableMutationLock()
        val pending = AtomicReference("course")
        val deleted = AtomicBoolean(false)
        val refreshHolding = CountDownLatch(1)
        val adoptStarted = CountDownLatch(1)

        val refresh = Thread {
            lock.exclusive {
                refreshHolding.countDown()
                assertTrue(adoptStarted.await(2, TimeUnit.SECONDS))
                pending.set(null)
            }
        }
        refresh.start()
        assertTrue(refreshHolding.await(2, TimeUnit.SECONDS))

        val adopt = Thread {
            adoptStarted.countDown()
            lock.exclusive {
                val key = pending.get()
                if (key != null) deleted.set(true)
            }
        }
        adopt.start()
        refresh.join(2000)
        adopt.join(2000)

        assertFalse(refresh.isAlive)
        assertFalse(adopt.isAlive)
        assertFalse(deleted.get())
        assertNull(pending.get())
    }
}
