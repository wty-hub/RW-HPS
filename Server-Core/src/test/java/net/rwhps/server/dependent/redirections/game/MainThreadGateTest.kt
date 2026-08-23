package net.rwhps.server.dependent.redirections.game

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MainThreadGateTest {

    @BeforeEach
    fun setUp() {
        MainThreadGate.resetForTest()
    }

    @AfterEach
    fun tearDown() {
        MainThreadGate.resetForTest()
    }

    @Test
    fun `runs inline when game loop has not started`() {
        val ran = AtomicBoolean(false)

        MainThreadGate.runExclusive { ran.set(true) }

        assertTrue(ran.get())
    }

    @Test
    fun `runs inline when already on the game thread`() {
        MainThreadGate.gameThread = Thread.currentThread()
        val ran = AtomicBoolean(false)

        MainThreadGate.runExclusive { ran.set(true) }

        assertTrue(ran.get())
    }

    @Test
    fun `nested exclusive call on game thread stays inline`() {
        MainThreadGate.gameThread = Thread.currentThread()
        val inner = AtomicBoolean(false)

        MainThreadGate.runExclusive {
            MainThreadGate.runExclusive { inner.set(true) }
        }

        assertTrue(inner.get())
    }

    @Test
    fun `queues work until drain on the game thread`() {
        MainThreadGate.gameThread = Thread("placeholder-game")
        val ran = AtomicBoolean(false)
        val callerDone = CountDownLatch(1)
        val caller = Thread {
            MainThreadGate.runExclusive { ran.set(true) }
            callerDone.countDown()
        }
        caller.start()
        Thread.sleep(50)
        assertTrue(caller.isAlive)
        assertFalse(ran.get())

        MainThreadGate.drain()

        assertTrue(callerDone.await(2, TimeUnit.SECONDS))
        assertTrue(ran.get())
        assertSame(Thread.currentThread(), MainThreadGate.gameThread)
        caller.join(1000)
    }

    @Test
    fun `queued exception is delivered to caller and later tasks still run`() {
        MainThreadGate.gameThread = Thread("placeholder-game")
        val barrier = CyclicBarrier(3)
        val firstError = AtomicReference<Throwable?>()
        val secondRan = AtomicBoolean(false)

        val caller1 = Thread {
            barrier.await()
            try {
                MainThreadGate.runExclusive { throw IllegalStateException("boom") }
            } catch (t: Throwable) {
                firstError.set(t)
            }
        }
        val caller2 = Thread {
            barrier.await()
            MainThreadGate.runExclusive { secondRan.set(true) }
        }
        caller1.start()
        caller2.start()
        barrier.await()
        Thread.sleep(50)

        MainThreadGate.drain()
        caller1.join(2000)
        caller2.join(2000)

        assertTrue(firstError.get() is IllegalStateException)
        assertEquals("boom", firstError.get()?.message)
        assertTrue(secondRan.get())
    }

    @Test
    fun `drain isolates a throwing task so the game loop can continue`() {
        MainThreadGate.gameThread = Thread("placeholder-game")
        val ran = AtomicInteger(0)
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)

        Thread {
            try {
                MainThreadGate.runExclusive {
                    ran.incrementAndGet()
                    throw RuntimeException("first")
                }
            } catch (_: RuntimeException) {
            } finally {
                first.countDown()
            }
        }.start()
        Thread {
            MainThreadGate.runExclusive { ran.incrementAndGet() }
            second.countDown()
        }.start()
        Thread.sleep(50)

        MainThreadGate.drain()

        assertTrue(first.await(2, TimeUnit.SECONDS))
        assertTrue(second.await(2, TimeUnit.SECONDS))
        assertEquals(2, ran.get())
    }

    @Test
    fun `inline exception propagates to caller`() {
        MainThreadGate.gameThread = Thread.currentThread()

        assertThrows(IllegalStateException::class.java) {
            MainThreadGate.runExclusive { throw IllegalStateException("inline") }
        }
    }
}
