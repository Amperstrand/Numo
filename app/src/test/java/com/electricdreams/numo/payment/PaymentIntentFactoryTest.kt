package com.electricdreams.numo.payment

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.electricdreams.numo.PaymentRequestActivity
import com.electricdreams.numo.core.data.model.PaymentHistoryEntry
import com.electricdreams.numo.feature.history.TransactionDetailActivity
import com.electricdreams.numo.feature.tips.TipSelectionActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PaymentIntentFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun entry(status: String = PaymentHistoryEntry.STATUS_PENDING) =
        PaymentHistoryEntry.createPending(
            amount = 1000,
            entryUnit = "sat",
            enteredAmount = 1000,
            bitcoinPrice = null,
            paymentRequest = null,
            formattedAmount = "1,000 sat",
        ).copy(rawStatus = status, lightningQuoteId = "quote-1")

    private fun retryOf(entry: PaymentHistoryEntry?, original: Intent = originalIntent()) =
        requireNotNull(PaymentIntentFactory.createRetryPaymentIntent(context, original, entry))

    private fun originalIntent() = Intent(context, PaymentRequestActivity::class.java).apply {
        putExtra(PaymentRequestActivity.EXTRA_PAYMENT_AMOUNT, 1000L)
        putExtra(PaymentRequestActivity.EXTRA_FORMATTED_AMOUNT, "1,000 sat")
        putExtra(TipSelectionActivity.EXTRA_TIP_AMOUNT_SATS, 100L)
        putExtra(PaymentRequestActivity.EXTRA_CHECKOUT_BASKET_JSON, "{\"items\":[]}")
        putExtra(PaymentRequestActivity.EXTRA_SAVED_BASKET_ID, "basket-1")
    }

    @Test
    fun `retry resumes a pending payment and keeps tip and basket`() {
        val pending = entry()

        val retry = retryOf(pending)

        assertEquals(pending.id, retry.getStringExtra(PaymentRequestActivity.EXTRA_RESUME_PAYMENT_ID))
        assertEquals("quote-1", retry.getStringExtra(PaymentRequestActivity.EXTRA_LIGHTNING_QUOTE_ID))
        assertEquals(100L, retry.getLongExtra(TipSelectionActivity.EXTRA_TIP_AMOUNT_SATS, 0))
        assertEquals("basket-1", retry.getStringExtra(PaymentRequestActivity.EXTRA_SAVED_BASKET_ID))
        assertEquals(1000L, retry.getLongExtra(PaymentRequestActivity.EXTRA_PAYMENT_AMOUNT, 0))
    }

    @Test
    fun `retry starts over when the payment expired or failed`() {
        val finished = listOf(PaymentHistoryEntry.STATUS_EXPIRED, PaymentHistoryEntry.STATUS_FAILED)
        finished.forEach { status ->
            val retry = retryOf(entry(status))

            assertFalse(retry.hasExtra(PaymentRequestActivity.EXTRA_RESUME_PAYMENT_ID))
            assertFalse(retry.hasExtra(PaymentRequestActivity.EXTRA_LIGHTNING_QUOTE_ID))
            assertEquals(1000L, retry.getLongExtra(PaymentRequestActivity.EXTRA_PAYMENT_AMOUNT, 0))
            assertEquals("basket-1", retry.getStringExtra(PaymentRequestActivity.EXTRA_SAVED_BASKET_ID))
        }
    }

    @Test
    fun `retry drops resume data left from an earlier attempt`() {
        val original = originalIntent().apply {
            putExtra(PaymentRequestActivity.EXTRA_RESUME_PAYMENT_ID, "older-attempt")
            putExtra(PaymentRequestActivity.EXTRA_NOSTR_SECRET_HEX, "old-secret")
        }

        val retry = retryOf(entry = null, original = original)

        assertFalse(retry.hasExtra(PaymentRequestActivity.EXTRA_RESUME_PAYMENT_ID))
        assertFalse(retry.hasExtra(PaymentRequestActivity.EXTRA_NOSTR_SECRET_HEX))
    }

    @Test
    fun `retry forwards the result to whoever started the payment`() {
        val retry = retryOf(entry())

        assertTrue(retry.flags and Intent.FLAG_ACTIVITY_FORWARD_RESULT != 0)
        assertTrue(retry.getBooleanExtra(PaymentRequestActivity.EXTRA_IS_RETRY, false))
    }

    @Test
    fun `nothing to retry once the payment completed`() {
        val completed = entry(PaymentHistoryEntry.STATUS_COMPLETED)

        assertNull(
            PaymentIntentFactory.createRetryPaymentIntent(context, originalIntent(), completed)
        )
    }

    @Test
    fun `details open the payment this screen created`() {
        val history = listOf(entry(), entry(), entry())

        val details = requireNotNull(
            PaymentIntentFactory.createPaymentDetailsIntent(context, history, history[1].id)
        )

        assertEquals(
            history[1].id,
            details.getStringExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID),
        )
        assertEquals(1, details.getIntExtra(TransactionDetailActivity.EXTRA_TRANSACTION_POSITION, -1))
    }

    @Test
    fun `details fall back to the latest entry for an unknown payment`() {
        val history = listOf(entry(), entry())

        val details = PaymentIntentFactory.createPaymentDetailsIntent(context, history, "unknown")

        assertEquals(
            history[1].id,
            details?.getStringExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID),
        )
    }

    @Test
    fun `no details without history`() {
        assertNull(PaymentIntentFactory.createPaymentDetailsIntent(context, emptyList(), "any"))
    }
}
