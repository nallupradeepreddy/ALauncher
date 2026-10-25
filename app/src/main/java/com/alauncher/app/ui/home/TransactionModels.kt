package com.alauncher.app.ui.home

data class MoneyAmount(
    val value: Double,
    val currencySymbol: String,
    val rawText: String
)

data class TransactionDetails(
    val type: TransactionAlertType,
    val amount: MoneyAmount?,
    val merchantOrBiller: String?,
    val dueDate: String?, // Normalized string representation e.g. "05-Jul"
    val accountSuffix: String?
)

object TransactionSettingsKeys {
    const val CATEGORY_BILL_TRANSACTIONAL = "BILL_TRANSACTIONAL"
    const val SUBTYPE_GROUP_DEBIT = "DEBIT"
    const val SUBTYPE_GROUP_BILL = "BILL_DUE"
    const val SUBTYPE_GROUP_RENEWAL = "SUBSCRIPTION_RENEWAL"
    const val SUBTYPE_GROUP_FAILED = "PAYMENT_FAILED"
}
