package com.pubnub.api.integration

import com.pubnub.api.PubNub
import com.pubnub.api.callbacks.SubscribeCallback
import com.pubnub.api.enums.PNHeartbeatNotificationOptions
import com.pubnub.api.enums.PNOperationType
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult
import com.pubnub.api.suite.await

import org.awaitility.Awaitility
import org.awaitility.Durations
import org.hamcrest.core.IsEqual
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PresenceIntegrationTests : BaseIntegrationTest() {

    @Test
    fun testWhereNow() {
        val expectedChannelsCount = 4
        val expectedChannels = generateSequence { randomValue() }.take(expectedChannelsCount).toList()

        pubnub.subscribeToBlocking(*expectedChannels.toTypedArray())

        pubnub.whereNow().await { result, status ->
            assertFalse(status.error)
            assertEquals(expectedChannelsCount, result!!.channels.size)
            assertEquals(expectedChannels.sorted(), result.channels.sorted())
        }
    }

    @Test
    fun testGlobalHereNow() {
        val expectedChannelsCount = 2
        val expectedClientsCount = 3

        val expectedChannels = generateSequence { randomValue() }.take(expectedChannelsCount).toList()

        val clients = mutableListOf(pubnub).apply {
            addAll(generateSequence { createPubNub() }.take(expectedClientsCount - 1).toList())
        }

        clients.forEach {
            it.subscribeToBlocking(*expectedChannels.toTypedArray())
        }

        wait(TIMEOUT_MEDIUM)

        pubnub.hereNow().apply {
            includeUUIDs = true
        }.await { result, status ->
            assertFalse(status.error)
            assertTrue(result!!.totalOccupancy >= expectedClientsCount)
            assertTrue(result.totalChannels >= expectedChannelsCount)
            assertTrue(result.channels.size >= expectedChannelsCount)

            assertTrue(result.channels.keys.containsAll(expectedChannels))

            result.channels.forEach { (key, value) ->
                if (expectedChannels.contains(key)) {
                    assertTrue(value.occupancy >= expectedClientsCount)
                    assertTrue(value.occupants.size >= expectedClientsCount)

                    assertEquals(
                        clients.map { it.configuration.uuid }.toList(),
                        value.occupants.map { it.uuid }.toList()
                    )
                }
            }
        }
    }

    @Test
    fun testHereNow() {
        val expectedChannelsCount = 2
        val expectedClientsCount = 3

        val expectedChannels = generateSequence { randomValue() }.take(expectedChannelsCount).toList()

        val clients = mutableListOf(pubnub).apply {
            addAll(generateSequence { createPubNub() }.take(expectedClientsCount - 1).toList())
        }

        clients.forEach {
            it.subscribeToBlocking(*expectedChannels.toTypedArray())
        }

        assertEquals(expectedChannelsCount, expectedChannels.size)
        assertEquals(expectedClientsCount, clients.size)

        wait(TIMEOUT_MEDIUM)

        pubnub.hereNow().apply {
            channels = expectedChannels
            includeUUIDs = true
        }.await { result, status ->
            assertFalse(status.error)
            assertEquals(expectedChannelsCount, result!!.totalChannels)
            assertEquals(expectedChannelsCount, result.channels.size)
            assertEquals(expectedChannelsCount * expectedClientsCount, result.totalOccupancy)
            result.channels.forEach { (key, value) ->
                assertTrue(expectedChannels.contains(key))
                assertTrue(expectedChannels.contains(value.channelName))
                assertEquals(expectedClientsCount, value.occupancy)
                assertEquals(expectedClientsCount, value.occupants.size)
                value.occupants.forEach { occupant ->
                    val uuid = occupant.uuid
                    var contains = false
                    for (client in clients) {
                        if (client.configuration.uuid == uuid) {
                            contains = true
                            break
                        }
                    }
                    assertTrue(contains)
                }
            }
        }
    }

    @Test
    fun testPresenceState() {
        val hits = AtomicInteger()
        val expectedHits = 2
        val expectedStatePayload = generatePayload()
        val expectedChannel = randomValue()

        pubnub.addListener(object : SubscribeCallback() {
            override fun status(pubnub: PubNub, pnStatus: PNStatus) {}
            override fun presence(pubnub: PubNub, pnPresenceEventResult: PNPresenceEventResult) {
                if (pnPresenceEventResult.event == "state-change"
                    && pnPresenceEventResult.channel == expectedChannel
                    && pnPresenceEventResult.uuid == pubnub.configuration.uuid
                ) {
                    assertEquals(expectedStatePayload, pnPresenceEventResult.state)
                    hits.incrementAndGet()
                }
            }
        })

        pubnub.subscribeToBlocking(expectedChannel)

        pubnub.setPresenceState().apply {
            channels = listOf(expectedChannel)
            state = expectedStatePayload
        }.await { result, status ->
            assertFalse(status.error)
            assertEquals(expectedStatePayload, result!!.state)
        }

        Awaitility.await()
            .atMost(Durations.FIVE_SECONDS)
            .untilAtomic(hits, IsEqual.equalTo(1))

        pubnub.getPresenceState().apply {
            channels = listOf(expectedChannel)
        }.await { result, status ->
            assertFalse(status.error)
            assertEquals(expectedStatePayload, result!!.stateByUUID[expectedChannel])
            hits.incrementAndGet()
        }

        Awaitility.await()
            .atMost(Durations.FIVE_SECONDS)
            .untilAtomic(hits, IsEqual.equalTo(expectedHits))
    }

    @Test
    fun testHeartbeatsDisabled() {
        val heartbeatCallsCount = AtomicInteger()
        val subscribeSuccess = AtomicBoolean()
        val expectedChannel = randomValue()

        pubnub.configuration.heartbeatNotificationOptions = PNHeartbeatNotificationOptions.ALL
        assertEquals(PNHeartbeatNotificationOptions.ALL, pubnub.configuration.heartbeatNotificationOptions)

        pubnub.configuration.presenceTimeout = 20;
        pubnub.configuration.heartbeatInterval = 0;

        assertEquals(20, pubnub.configuration.presenceTimeout)
        assertEquals(0, pubnub.configuration.heartbeatInterval)

        pubnub.addListener(object : SubscribeCallback() {
            override fun status(pubnub: PubNub, pnStatus: PNStatus) {
                if (!pnStatus.error && pnStatus.affectedChannels.contains(expectedChannel)) {
                    if (pnStatus.operation == PNOperationType.PNSubscribeOperation) {
                        subscribeSuccess.set(true)
                    }
                    if (pnStatus.operation == PNOperationType.PNHeartbeatOperation) {
                        heartbeatCallsCount.incrementAndGet()
                    }
                }
            }
        })

        pubnub.subscribe().apply {
            channels = listOf(expectedChannel)
            withPresence = true
        }.execute()

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollDelay(19, TimeUnit.SECONDS)
            .until {
                subscribeSuccess.get() && heartbeatCallsCount.get() == 0
            }
    }

    @Test
    fun testHeartbeatsEnabled() {
        val heartbeatCallsCount = AtomicInteger()
        val subscribeSuccess = AtomicBoolean()
        val expectedChannel: String = randomValue()

        pubnub.configuration.heartbeatNotificationOptions = PNHeartbeatNotificationOptions.ALL
        assertEquals(PNHeartbeatNotificationOptions.ALL, pubnub.configuration.heartbeatNotificationOptions)

        pubnub.configuration.presenceTimeout = 20

        assertEquals(20, pubnub.configuration.presenceTimeout)
        assertEquals(9, pubnub.configuration.heartbeatInterval)

        pubnub.addListener(object : SubscribeCallback() {
            override fun status(pubnub: PubNub, pnStatus: PNStatus) {
                println(pnStatus.operation)
                if (!pnStatus.error && pnStatus.affectedChannels.contains(expectedChannel)) {
                    if (pnStatus.operation == PNOperationType.PNSubscribeOperation) {
                        subscribeSuccess.set(true)
                    }
                    if (pnStatus.operation == PNOperationType.PNHeartbeatOperation) {
                        heartbeatCallsCount.incrementAndGet()
                    }
                }
            }
        })

        pubnub.subscribe().apply {
            channels = listOf(expectedChannel)
            withPresence = true
        }.execute()

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .until {
                subscribeSuccess.get() && heartbeatCallsCount.get() > 2
            }
    }
}
