package com.pubnub.api.legacy.endpoints.history

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.pubnub.api.legacy.BaseTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FetchMessagesEndpointTest : BaseTest() {

    @Test
    fun testSyncSuccess() {
        stubFor(
            get(urlPathEqualTo("/v3/history/sub-key/mySubscribeKey/channel/mychannel,my_channel"))
                .willReturn(
                    aResponse().withBody(
                        """
                            {
                              "status": 200,
                              "error": false,
                              "error_message": "",
                              "channels": {
                                "my_channel": [
                                  {
                                    "message": "hihi",
                                    "timetoken": "14698320467224036"
                                  },
                                  {
                                    "message": "Hey",
                                    "timetoken": "14698320468265639"
                                  }
                                ],
                                "mychannel": [
                                  {
                                    "message": "sample message",
                                    "timetoken": "14369823849575729"
                                  }
                                ]
                              }
                            }
                        """.trimIndent()
                    )
                )
        )


        val response = pubnub.fetchMessages().apply {
            channels = listOf("mychannel", "my_channel")
            maximumPerChannel = 25
        }.sync()!!

        assertEquals(response.channels.size, 2)
        assertTrue(response.channels.containsKey("mychannel"))
        assertTrue(response.channels.containsKey("my_channel"))
        assertEquals(response.channels["mychannel"]!!.size, 1)
        assertEquals(response.channels["my_channel"]!!.size, 2)
    }

    @Test
    fun testSyncAuthSuccess() {
        stubFor(
            get(urlPathEqualTo("/v3/history/sub-key/mySubscribeKey/channel/mychannel,my_channel"))
                .willReturn(
                    aResponse().withBody(
                        """
                            {
                              "status": 200,
                              "error": false,
                              "error_message": "",
                              "channels": {
                                "my_channel": [
                                  {
                                    "message": "hihi",
                                    "timetoken": "14698320467224036"
                                  },
                                  {
                                    "message": "Hey",
                                    "timetoken": "14698320468265639"
                                  }
                                ],
                                "mychannel": [
                                  {
                                    "message": "sample message",
                                    "timetoken": "14369823849575729"
                                  }
                                ]
                              }
                            }
                        """.trimIndent()
                    )
                )
        )

        pubnub.configuration.authKey = "authKey"

        val response = pubnub.fetchMessages().apply {
            channels = listOf("mychannel", "my_channel")
            maximumPerChannel = 25
        }.sync()!!

        assertEquals(response.channels.size, 2)
        assertTrue(response.channels.containsKey("mychannel"))
        assertTrue(response.channels.containsKey("my_channel"))
        assertEquals(response.channels["mychannel"]!!.size, 1)
        assertEquals(response.channels["my_channel"]!!.size, 2)
        val requests = findAll(getRequestedFor(urlMatching("/.*")))
        assertEquals("authKey", requests[0].queryParameter("auth").firstValue())
        assertEquals(1, requests.size)
    }

    @Test
    fun testSyncEncryptedSuccess() {
        pubnub.configuration.cipherKey = "testCipher"

        stubFor(
            get(urlPathEqualTo("/v3/history/sub-key/mySubscribeKey/channel/mychannel,my_channel"))
                .willReturn(
                    aResponse().withBody(
                        """
                            {
                              "status": 200,
                              "error": false,
                              "error_message": "",
                              "channels": {
                                "my_channel": [
                                  {
                                    "message": "jC/yJ2y99BeYFYMQ7c53pg==",
                                    "timetoken": "14797423056306675"
                                  }
                                ],
                                "mychannel": [
                                  {
                                    "message": "jC/yJ2y99BeYFYMQ7c53pg==",
                                    "timetoken": "14797423056306675"
                                  }
                                ]
                              }
                            }
                        """.trimIndent()
                    )
                )
        )

        val response = pubnub.fetchMessages().apply {
            channels = listOf("mychannel", "my_channel")
            maximumPerChannel = 25
        }.sync()!!

        assertEquals(response.channels.size, 2)
        assertTrue(response.channels.containsKey("mychannel"))
        assertTrue(response.channels.containsKey("my_channel"))
        assertEquals(response.channels["mychannel"]!!.size, 1)
        assertEquals(response.channels["my_channel"]!!.size, 1)
    }

}