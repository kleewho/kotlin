package com.pubnub.api.integration

import com.pubnub.api.PubNub
import com.pubnub.api.callbacks.SubscribeCallback
import com.pubnub.api.enums.PNOperationType
import com.pubnub.api.enums.PNStatusCategory
import com.pubnub.api.listen
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class SubscribeIntegrationTests : BaseIntegrationTest() {

    lateinit var guestClient: PubNub

    override fun onBefore() {
        guestClient = createPubNub()
    }

    @Test
    fun testSubscribeToMultipleChannels() {
        val expectedChannelList = generateSequence { randomValue() }.take(3).toList()

        pubnub.subscribe().apply {
            channels = expectedChannelList
            withPresence = true
        }.execute()

        wait()

        assertEquals(3, pubnub.getSubscribedChannels().size)
        assertTrue(pubnub.getSubscribedChannels().contains(expectedChannelList[0]))
        assertTrue(pubnub.getSubscribedChannels().contains(expectedChannelList[1]))
        assertTrue(pubnub.getSubscribedChannels().contains(expectedChannelList[2]))
    }

    @Test
    fun testSubscribeToChannel() {
        val expectedChannel = randomValue()

        pubnub.subscribe().apply {
            channels = listOf(expectedChannel)
            withPresence = true
        }.execute()

        wait()

        assertEquals(1, pubnub.getSubscribedChannels().size)
        assertTrue(pubnub.getSubscribedChannels().contains(expectedChannel))
    }

    @Test
    fun testWildcardSubscribe() {
        val success = AtomicBoolean()

        val expectedMessage = randomValue()

        pubnub.subscribeToBlocking("my.*")

        pubnub.addListener(object : SubscribeCallback() {
            override fun status(pubnub: PubNub, pnStatus: PNStatus) {}

            override fun message(pubnub: PubNub, pnMessageResult: PNMessageResult) {
                assertEquals(expectedMessage, pnMessageResult.message.asString)
                success.set(true)
            }
        })

        guestClient.publish().apply {
            channel = "my.test"
            message = expectedMessage
        }.sync()!!

        success.listen()
    }

    @Test
    fun testUnsubscribeFromChannel() {
        val success = AtomicBoolean()

        val expectedChannel = randomValue()

        pubnub.subscribeToBlocking(expectedChannel)

        pubnub.addListener(object : SubscribeCallback() {
            override fun status(pubnub: PubNub, pnStatus: PNStatus) {
                if (pnStatus.operation == PNOperationType.PNUnsubscribeOperation
                    && pnStatus.category == PNStatusCategory.PNAcknowledgmentCategory
                ) {
                    success.set(pubnub.getSubscribedChannels().none { it == expectedChannel })
                }
            }
        })

        pubnub.unsubscribeFromBlocking(expectedChannel)

        success.listen()
    }

    @Test
    fun testUnsubscribeFromAllChannels() {
        val success = AtomicBoolean()

        pubnub.addListener(object : SubscribeCallback() {
            override fun status(pubnub: PubNub, pnStatus: PNStatus) {
                success.set(pubnub.getSubscribedChannels().isEmpty())
            }
        })

        pubnub.unsubscribeAll()

        success.listen()
    }

}
