package com.mycelium.wapi.wallet.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.mycelium.wapi.wallet.FeeEstimationsGeneric
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.eth.EthBlockchainService
import com.mycelium.wapi.wallet.eth.coins.EthMain
import com.mycelium.wapi.wallet.eth.coins.EthTest
import com.mycelium.wapi.wallet.genericdb.FeeEstimationsBacking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger


class EthFeeProvider(
    testnet: Boolean,
    private val service: EthBlockchainService,
    private val feeBacking: FeeEstimationsBacking
) : FeeProvider {

    override val coinType = if (testnet) {
        EthTest
    } else {
        EthMain
    }
    override var estimation: FeeEstimationsGeneric = feeBacking.getEstimationForCurrency(coinType)
        ?: FeeEstimationsGeneric(
            Value.valueOf(coinType, 1000000000),
            Value.valueOf(coinType, 33000000000),
            Value.valueOf(coinType, 67000000000),
            Value.valueOf(coinType, 100000000000),
            0
        )

    override suspend fun updateFeeEstimationsAsync() {
        estimation = withContext(Dispatchers.IO) {
            try {
                val newEstimation = getGasPriceEstimates()
                feeBacking.updateFeeEstimation(newEstimation.feeEstimation())
                return@withContext newEstimation
            } catch (e: Exception) {
                return@withContext estimation
            }
        }
    }


    private fun getGasPriceEstimates(): FeeEstimationsGeneric {
        val low = service.feeEstimation(PriceLevelSpeed.SAFE_LOW.block)
        val economy = service.feeEstimation(PriceLevelSpeed.AVERAGE.block)
        val fast = service.feeEstimation(PriceLevelSpeed.FAST.block)
        val fastest = service.feeEstimation(PriceLevelSpeed.FASTEST.block)
        return FeeEstimationsGeneric(
            low = Value.parse(coinType, low.result),
            economy = Value.parse(coinType, economy.result),
            normal = Value.parse(coinType, fast.result),
            high = Value.parse(coinType, fastest.result),
            lastCheck = System.currentTimeMillis()
        )
    }

    private fun convertBigIntegerToValue(value: BigInteger) = Value.valueOf(coinType, value)

    /**
     * Estimates are provided by https://github.com/AlhimicMan/eip1559_gas_estimator
     */
    private class GasPriceEstimates {
        @JsonProperty("base_fee_per_gas")
        var baseFeePerGas: BigInteger = BigInteger.ZERO

        @JsonProperty("price_levels")
        var priceLevels: List<PriceLevels> = emptyList()

        private class PriceLevels {
            var speed: PriceLevelSpeed = PriceLevelSpeed.SAFE_LOW

            @JsonProperty("max_fee_per_gas")
            var maxFeePerGas: BigInteger = BigInteger.ZERO

            @JsonProperty("max_priority_fee_per_gas")
            var maxPriorityFeePerGas: BigInteger = BigInteger.ZERO
        }

        fun getPrice(priceLevelSpeed: PriceLevelSpeed) =
            baseFeePerGas + priceLevels.first { it.speed == priceLevelSpeed }.maxPriorityFeePerGas
    }

    private enum class PriceLevelSpeed(val block: Int) {
        @JsonProperty("safe_low")
        SAFE_LOW(15),

        @JsonProperty("average")
        AVERAGE(6),

        @JsonProperty("fast")
        FAST(3),

        @JsonProperty("fastest")
        FASTEST(1),
    }

    companion object {
        private const val GAS_PRICE_ESTIMATES_ADDRESS = "https://bb-eth.mycelium.com:8181/eth"
    }
}