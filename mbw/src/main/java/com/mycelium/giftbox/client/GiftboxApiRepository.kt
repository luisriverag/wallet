package com.mycelium.giftbox.client

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.mycelium.bequant.kyc.inputPhone.coutrySelector.CountryModel
import com.mycelium.bequant.remote.doRequest
import com.mycelium.generated.giftbox.database.GiftboxCard
import com.mycelium.generated.giftbox.database.GiftboxDB
import com.mycelium.generated.giftbox.database.GiftboxProduct
import com.mycelium.giftbox.client.models.CheckoutProductResponse
import com.mycelium.giftbox.client.models.CreateOrderRequest
import com.mycelium.giftbox.client.models.Order
import com.mycelium.giftbox.client.models.OrderResponse
import com.mycelium.giftbox.client.models.OrdersHistoryResponse
import com.mycelium.giftbox.client.models.PriceResponse
import com.mycelium.giftbox.client.models.ProductResponse
import com.mycelium.giftbox.client.models.ProductsResponse
import com.mycelium.giftbox.dateAdapter
import com.mycelium.giftbox.listBigDecimalAdapter
import com.mycelium.giftbox.model.Card
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.WalletApplication
import com.mycelium.wapi.wallet.AesKeyCipher
import com.mycelium.wapi.wallet.genericdb.Adapters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GiftboxApiRepository {
    private var lastOrderId = updateOrderId()

    private val api = GiftboxApi.create()
    private val giftbxDB = GiftboxDB.invoke(
        AndroidSqliteDriver(GiftboxDB.Schema, WalletApplication.getInstance(), "giftbox.db"),
        GiftboxCard.Adapter(dateAdapter,dateAdapter),
        GiftboxProduct.Adapter(
            Adapters.listAdapter, Adapters.listAdapter,
            Adapters.bigDecimalAdapter, Adapters.bigDecimalAdapter,
            listBigDecimalAdapter)
    )

    private val clientUserIdFromMasterSeed by lazy {
        MbwManager.getInstance(WalletApplication.getInstance())
                .masterSeedManager.getIdentityAccountKeyManager(AesKeyCipher.defaultKeyCipher())
                .getPrivateKeyForWebsite(GiftboxConstants.WEBSITE, AesKeyCipher.defaultKeyCipher())
                .publicKey.toString()
    }

    private fun updateOrderId(): String {
        lastOrderId = UUID.randomUUID().toString()
        return lastOrderId
    }

    private fun parseActivateBy(activateBy: String?): Date? {
        return activateBy?.let {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun getPrice(
        scope: CoroutineScope,
        code: String,
        quantity: Int,
        amount: Int,
        currencyId: String,
        success: (PriceResponse?) -> Unit,
        error: (Int, String) -> Unit,
        finally: () -> Unit
    ) {
        doRequest(scope, {
            api.price(
                clientUserIdFromMasterSeed,
                lastOrderId,
                amount,
                quantity,
                code,
                currencyId
            )
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun getProduct(
        scope: CoroutineScope,
        productId: String,
        success: (ProductResponse?) -> Unit,
        error: (Int, String) -> Unit,
        finally: () -> Unit = {}
    ): Job {
        return doRequest(scope, {
            api.product(
                clientUserIdFromMasterSeed,
                lastOrderId,
                productId
            )
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun getProducts(
        scope: CoroutineScope,
        search: String? = null,
        country: List<CountryModel>? = null,
        category: String? = null,
        offset: Long = 0,
        limit: Long = 100,
        success: (ProductsResponse?) -> Unit,
        error: (Int, String) -> Unit,
        finally: (() -> Unit)? = null
    ) : Job {
        val countryString = country?.joinToString(",") { it.acronym }
        return doRequest(scope, {
            api.products(
                clientUserIdFromMasterSeed,
                lastOrderId,
                category,
                search,
                if(countryString?.isNotEmpty() == true) countryString else null,
                offset,
                limit
            )
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun createOrder(
        scope: CoroutineScope,
        code: String,
        quantity: Int,
        amount: Int,
        currencyId: String,
        success: (OrderResponse?) -> Unit,
        error: (Int, String) -> Unit,
        finally: (() -> Unit)? = null
    ) {
        updateOrderId()
        doRequest(scope, {
            api.createOrder(
                CreateOrderRequest(
                    clientUserId = clientUserIdFromMasterSeed,
                    clientOrderId = lastOrderId,
                    code = code,
                    quantity = quantity.toString(),
                    amount = amount.toString(),
                    currencyId = currencyId
                )
            )
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun checkoutProduct(
        scope: CoroutineScope,
        code: String,
        quantity: Int,
        amount: Int,
        currencyId: String? = null,
        success: (CheckoutProductResponse?) -> Unit,
        error: (Int, String) -> Unit,
        finally: () -> Unit
    ) {
        doRequest(scope, {
            api.checkoutProduct(
                clientUserIdFromMasterSeed,
                lastOrderId,
                code,
                quantity,
                amount,
                currencyId
            )
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun getOrders(
        scope: CoroutineScope,
        offset: Long = 0,
        limit: Long = 100,
        success: (OrdersHistoryResponse?) -> Unit,
        error: ((Int, String) -> Unit)? = null,
        finally: (() -> Unit)? = null
    ) {
        doRequest(scope, {
            api.orders(clientUserIdFromMasterSeed, offset, limit).apply {
                if(this.isSuccessful) {
                    updateCards(this.body()?.items)
                    if (offset == 0L) {
                        fetchAllOrders(scope, limit, this.body()?.size ?: 0)
                    }
                }
            }
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun getOrder(
        scope: CoroutineScope,
        clientOrderId: String = lastOrderId,
        success: (OrderResponse?) -> Unit,
        error: (Int, String) -> Unit,
        finally: (() -> Unit)? = null
    ) {
        doRequest(scope, {
            api.order(clientUserIdFromMasterSeed, clientOrderId)
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    private fun fetchAllOrders(scope: CoroutineScope, offset: Long, count: Long) {
        for (i in offset..count step 100) {
            getOrders(scope, i, 100, success = {
            })
        }
    }

    private fun updateCards(orders: List<Order>?) {
        orders?.forEach { order ->
            order.items?.forEach {
                giftbxDB.giftboxCardQueries.updateCard(order.productCode, order.productName, order.productImg,
                        order.currencyCode, it.amount, it.expiryDate, parseActivateBy(it.activateBy), order.timestamp,
                        order.clientOrderId ?: "", it.code ?: "",
                        it.deliveryUrl ?: "", it.pin ?: "")
                if (giftbxDB.giftboxCardQueries.isCardUpdated().executeAsOne() == 0L) {
                    giftbxDB.giftboxCardQueries.insertCard(order.clientOrderId ?: "",
                            order.productCode, order.productName, order.productImg, order.currencyCode,
                            it.amount, it.expiryDate, it.code ?: "", parseActivateBy(it.activateBy), it.deliveryUrl ?: "",
                            it.pin ?: "", order.timestamp)
                }
            }
        }
    }

    fun getCards(scope: CoroutineScope,
                 success: (List<Card>?) -> Unit,
                 error: (Int, String) -> Unit,
                 finally: (() -> Unit)? = null) {
        doRequest(scope, {
            Response.success(giftbxDB.giftboxCardQueries.selectCards(mapper = { clientOrderId: String,
                                                                                productCode: String?,
                                                                                productName: String?,
                                                                                productImg: String?,
                                                                                currencyCode: String?,
                                                                                amount: String?,
                                                                                expiryDate: String?,
                                                                                code: String,
                                                                                activateBy: Date?,
                                                                                deliveryUrl: String,
                                                                                pin: String,
                                                                                timestamp: Date?,
                                                                                redeemed: Boolean ->
                Card(clientOrderId, productCode, productName, productImg, currencyCode, amount, expiryDate, code, activateBy, deliveryUrl, pin, timestamp, redeemed)
            }).executeAsList())
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun redeem(card: Card, scope: CoroutineScope,
               success: (Boolean?) -> Unit) {
        doRequest(scope, {
            giftbxDB.giftboxCardQueries.redeemCard(card.clientOrderId, card.code, card.deliveryUrl, card.pin)
            Response.success(true)
        }, successBlock = success)
    }

    fun unredeem(card: Card, scope: CoroutineScope,
               success: (Boolean?) -> Unit) {
        doRequest(scope, {
            giftbxDB.giftboxCardQueries.unredeemCard(card.clientOrderId, card.code, card.deliveryUrl, card.pin)
            Response.success(true)
        }, successBlock = success)
    }

    fun remove(card: Card, scope: CoroutineScope,
               success: (Boolean?) -> Unit) {
        doRequest(scope, {
            giftbxDB.giftboxCardQueries.deleteCard(card.clientOrderId, card.code, card.deliveryUrl, card.pin)
            Response.success(true)
        }, successBlock = success)
    }
}