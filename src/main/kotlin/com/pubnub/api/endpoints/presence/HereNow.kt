package com.pubnub.api.endpoints.presence

import com.google.gson.JsonElement
import com.pubnub.api.Endpoint
import com.pubnub.api.PubNub
import com.pubnub.api.enums.PNOperationType
import com.pubnub.api.models.consumer.presence.PNHereNowChannelData
import com.pubnub.api.models.consumer.presence.PNHereNowOccupantData
import com.pubnub.api.models.consumer.presence.PNHereNowResult
import com.pubnub.api.models.server.Envelope
import com.pubnub.api.toCsv
import retrofit2.Call
import retrofit2.Response
import java.util.*

class HereNow(pubnub: PubNub) : Endpoint<Envelope<JsonElement>, PNHereNowResult>(pubnub) {

    var channels = listOf<String>()
    var channelGroups = listOf<String>()
    var includeState = false
    var includeUUIDs = true

    private fun isGlobalHereNow() = channels.isEmpty() && channelGroups.isEmpty()

    override fun getAffectedChannels() = channels
    override fun getAffectedChannelGroups() = channelGroups

    override fun doWork(queryParams: HashMap<String, String>): Call<Envelope<JsonElement>> {
        if (includeState) {
            queryParams["state"] = "1"
        }
        if (!includeUUIDs) {
            queryParams["disable_uuids"] = "1"
        }
        if (channelGroups.isNotEmpty()) {
            queryParams["channel-group"] = channelGroups.toCsv()
        }

        return if (!isGlobalHereNow()) {
            pubnub.retrofitManager.presenceService.hereNow(
                pubnub.configuration.subscribeKey,
                channels.toCsv(),
                queryParams
            )
        } else {
            pubnub.retrofitManager.presenceService.globalHereNow(
                pubnub.configuration.subscribeKey,
                queryParams
            )
        }
    }

    override fun createResponse(input: Response<Envelope<JsonElement>>): PNHereNowResult? {
        return if (isGlobalHereNow()) {
            parseMultipleChannelResponse(input.body()?.payload!!)
        } else {
            if (channels.size > 1 || channelGroups.isNotEmpty()) {
                parseMultipleChannelResponse(input.body()?.payload!!)
            } else {
                parseSingleChannelResponse(input.body()!!)
            }
        }
    }

    private fun parseSingleChannelResponse(input: Envelope<JsonElement>): PNHereNowResult {
        val pnHereNowResult = PNHereNowResult(
            totalChannels = 1,
            totalOccupancy = input.occupancy
        )

        val pnHereNowChannelData = PNHereNowChannelData(
            channelName = channels[0],
            occupancy = input.occupancy
        )

        if (includeUUIDs) {
            pnHereNowChannelData.occupants = prepareOccupantData(input.uuids!!)
            pnHereNowResult.channels[channels[0]] = pnHereNowChannelData
        }

        return pnHereNowResult
    }

    private fun parseMultipleChannelResponse(input: JsonElement): PNHereNowResult {
        val pnHereNowResult = PNHereNowResult(
            totalChannels = pubnub.mapper.elementToInt(input, "total_channels"),
            totalOccupancy = pubnub.mapper.elementToInt(input, "total_occupancy")
        )

        val it = pubnub.mapper.getObjectIterator(input, "channels")

        while (it.hasNext()) {
            val entry = it.next()
            val pnHereNowChannelData = PNHereNowChannelData(
                channelName = entry.key,
                occupancy = pubnub.mapper.elementToInt(entry.value, "occupancy")
            )
            if (includeUUIDs) {
                pnHereNowChannelData.occupants = prepareOccupantData(pubnub.mapper.getField(entry.value, "uuids")!!)
            }
            pnHereNowResult.channels[entry.key] = pnHereNowChannelData
        }

        return pnHereNowResult
    }

    private fun prepareOccupantData(input: JsonElement): MutableList<PNHereNowOccupantData> {
        val occupantsResults = mutableListOf<PNHereNowOccupantData>()

        val it = pubnub.mapper.getArrayIterator(input)
        while (it?.hasNext()!!) {
            val occupant = it.next()
            occupantsResults.add(
                if (includeState) {
                    PNHereNowOccupantData(
                        uuid = pubnub.mapper.elementToString(occupant, "uuid")!!,
                        state = pubnub.mapper.getField(occupant, "state")
                    )
                } else {
                    PNHereNowOccupantData(
                        uuid = pubnub.mapper.elementToString(occupant)!!
                    )
                }
            )
        }

        return occupantsResults
    }

    override fun operationType() = PNOperationType.PNHereNowOperation
}