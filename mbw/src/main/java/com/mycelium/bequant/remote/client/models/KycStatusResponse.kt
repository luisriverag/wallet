/**
* Auth API
* Auth API<br> <a href='/changelog'>Changelog</a>
*
* The version of the OpenAPI document: v0.0.50
* 
*
* NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
* https://openapi-generator.tech
* Do not edit the class manually.
*/
package com.mycelium.bequant.remote.client.models



import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 
 * @param message 
 * @param rejectReason 
 * @param requestReason 
 * @param status 
 */
data class KycStatusResponse (
    @JsonProperty("status")
    val status: kotlin.String,
    @JsonProperty("message")
    val message: kotlin.String? = null,
    @JsonProperty("reject_reason")
    val rejectReason: kotlin.String? = null,
    @JsonProperty("request_reason")
    val requestReason: kotlin.String? = null
)

