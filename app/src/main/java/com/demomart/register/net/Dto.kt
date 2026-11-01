package com.demomart.register.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    @SerialName("customer_ref") val customerRef: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("card_display") val cardDisplay: String,
    val balance: Long,
)

@Serializable
data class QuoteOut(
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("redeem_points") val redeemPoints: Long,
    @SerialName("discount_cents") val discountCents: Long,
    @SerialName("paid_cents") val paidCents: Long,
    @SerialName("earn_points") val earnPoints: Long,
    @SerialName("max_redeem_points") val maxRedeemPoints: Long,
)

@Serializable
data class SaleResult(
    @SerialName("sale_id") val saleId: Long,
    @SerialName("register_id") val registerId: String,
    @SerialName("receipt_no") val receiptNo: Long,
    @SerialName("display_name") val displayName: String?,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("redeem_points") val redeemPoints: Long,
    @SerialName("paid_cents") val paidCents: Long,
    @SerialName("earned_points") val earnedPoints: Long,
    val status: String,
    val balance: Long?,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NextReceipt(
    @SerialName("register_id") val registerId: String,
    @SerialName("next_receipt_no") val nextReceiptNo: Long,
)

@Serializable
data class IdentifyIn(val token: String)

@Serializable
data class QuoteIn(
    @SerialName("customer_ref") val customerRef: String?,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("redeem_points") val redeemPoints: Long,
)

@Serializable
data class CommitIn(
    @SerialName("register_id") val registerId: String,
    @SerialName("receipt_no") val receiptNo: Long,
    @SerialName("customer_ref") val customerRef: String?,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("redeem_points") val redeemPoints: Long,
)

@Serializable
data class RefundIn(
    @SerialName("register_id") val registerId: String,
    @SerialName("receipt_no") val receiptNo: Long,
)

@Serializable
internal data class ErrorBody(val error: String, val message: String)
