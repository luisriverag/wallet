/**
 * API
 * Create API keys in your profile and use public API key as username and secret as password to authorize.
 *
 * The version of the OpenAPI document: 2.19.0
 *
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
package com.mycelium.bequant.remote.trading.model


import com.squareup.moshi.Json
import java.math.BigDecimal
import java.math.BigInteger

/**
 *
 * @param currency
 * @param available Amount available to spend
 * @param reserved Amount reserved on orders or payout
 */

data class Balance(
        @Json(name = "currency")
        val currency: kotlin.String? = null,
        /* Amount available to spend */
        @Json(name = "available")
        val available: BigInteger? = null,
        /* Amount reserved on orders or payout */
        @Json(name = "reserved")
        val reserved: BigInteger? = null
)

