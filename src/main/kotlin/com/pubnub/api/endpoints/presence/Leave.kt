package com.pubnub.api.endpoints.presence

import com.pubnub.api.*
import com.pubnub.api.enums.PNOperationType
import retrofit2.Call
import retrofit2.Response
import java.util.*

internal class Leave(pubnub: PubNub) : Endpoint<Any, Boolean>(pubnub) {

    var channels = emptyList<String>()
    var channelGroups = emptyList<String>()

    override fun validateParams() {
        super.validateParams()
        if (channels.isEmpty() && channelGroups.isEmpty()) {
            throw PubNubException(PubNubError.CHANNEL_AND_GROUP_MISSING)
        }
    }

    override fun getAffectedChannels() = channels
    override fun getAffectedChannelGroups() = channelGroups

    override fun doWork(queryParams: HashMap<String, String>): Call<Any> {
        queryParams["channel-group"] = channelGroups.toCsv()

        return pubnub.retrofitManager.presenceService.leave(
            pubnub.configuration.subscribeKey,
            channels.toCsv(),
            queryParams
        )
    }

    override fun createResponse(input: Response<Any>) = true

    override fun operationType() = PNOperationType.PNUnsubscribeOperation
}


