/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.selects

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.intrinsics.*
import kotlin.test.*

class SelectChannelStressTest: TestBase() {

    // Running less iterations on native platforms because of some performance regression
    private val iterations = (if (isNative) 1_000 else 1_000_000) * stressTestMultiplier

    @Test
    fun testSelectSendResourceCleanupArrayChannel() = runMtTest {
        val channel = Channel<Int>(1)
        expect(1)
        channel.send(-1) // fill the buffer, so all subsequent sends cannot proceed
        repeat(iterations) { i ->
            select {
                channel.onSend(i) { expectUnreached() }
                default { expect(i + 2) }
            }
        }
        finish(iterations + 2)
    }

    @Test
    fun testSelectReceiveResourceCleanupArrayChannel() = runMtTest {
        val channel = Channel<Int>(1)
        expect(1)
        repeat(iterations) { i ->
            select {
                channel.onReceive { expectUnreached() }
                default { expect(i + 2) }
            }
        }
        finish(iterations + 2)
    }

    @Test
    fun testSelectSendResourceCleanupRendezvousChannel() = runMtTest {
        val channel = Channel<Int>(Channel.RENDEZVOUS)
        expect(1)
        repeat(iterations) { i ->
            select {
                channel.onSend(i) { expectUnreached() }
                default { expect(i + 2) }
            }
        }
        finish(iterations + 2)
    }

    @Test
    fun testSelectReceiveResourceRendezvousChannel() = runMtTest {
        val channel = Channel<Int>(Channel.RENDEZVOUS)
        expect(1)
        repeat(iterations) { i ->
            select {
                channel.onReceive { expectUnreached() }
                default { expect(i + 2) }
            }
        }
        finish(iterations + 2)
    }

    internal fun <R> SelectBuilder<R>.default(block: suspend () -> R) {
        this as SelectBuilderImpl // type assertion
        if (!trySelect()) return
        block.startCoroutineUnintercepted(this)
    }
}
