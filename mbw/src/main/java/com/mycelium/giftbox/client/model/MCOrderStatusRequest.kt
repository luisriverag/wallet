package com.mycelium.giftbox.client.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.Parcelize

//"user_id": "123456",
//"order_id": "12345",
data class MCOrderStatusRequest(
    @JsonProperty("user_id")
    var userId: String,
    @JsonProperty("order_id")
    var orderId: String
)
