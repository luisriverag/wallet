package com.mycelium.giftbox.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Card(
        val clientOrderId: String,
        val productCode: String? = null,
        val productName: String? = null,
        val productImg: String? = null,
        val currencyCode: String? = null,
        val amount: String? = null,
        val expiryDate: String? = null,
        val code: String,
        val activateBy: Date? = null,
        val deliveryUrl: String,
        val pin: String,
        val timestamp: Date? = null,
        val isRedeemed: Boolean
) : Parcelable