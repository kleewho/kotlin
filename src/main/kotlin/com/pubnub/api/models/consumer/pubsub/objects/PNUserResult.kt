package com.pubnub.api.models.consumer.pubsub.objects

import com.google.gson.JsonElement
import com.pubnub.api.models.consumer.pubsub.BasePubSubResult

class PNUserResult(
    override val result: BasePubSubResult,
    override val event: String,
    override val data: JsonElement
) : ObjectResult<JsonElement>(
    result,
    event,
    data
)