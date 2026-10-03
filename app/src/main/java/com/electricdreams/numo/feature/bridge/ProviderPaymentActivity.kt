package com.electricdreams.numo.feature.bridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.electricdreams.numo.R
import com.electricdreams.numo.core.bridge.BridgeClient
import com.electricdreams.numo.core.bridge.BridgePrefs
import com.electricdreams.numo.databinding.ActivityProviderPaymentBinding
import com.electricdreams.numo.ui.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Provider-mode payment screen: renders the bridge-issued bolt11 (the 402
 * top-up invoice) as a QR, polls the bridge until the invoice settles, then
 * submits the venue order and shows the order number (+ Mollie link in live).
 * The self-generated mint-quote flow is never entered in this mode.
 */
class ProviderPaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderPaymentBinding
    private lateinit var prefs: BridgePrefs
    private var submitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = BridgePrefs.getInstance(this)

        val bolt11 = intent.getStringExtra(EXTRA_BOLT11).orEmpty()
        val amountSats = intent.getLongExtra(EXTRA_AMOUNT_SATS, 0L)
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "demo"

        binding.providerAmount.text = getString(R.string.provider_amount_sats, amountSats)
        binding.providerStatus.text = getString(
            if (mode == "demo") R.string.provider_waiting_demo else R.string.provider_waiting_live
        )

        val qr = QrCodeGenerator.generate(bolt11, 512, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT)
        binding.providerQr.setImageBitmap(qr)

        binding.providerDone.setOnClickListener { finish() }

        lifecycleScope.launch {
            while (!submitted) {
                val paid = withContext(Dispatchers.IO) {
                    runCatching { BridgeClient.pollOrder(prefs, orderId) }
                        .getOrNull()
                        ?.optString("status") == "paid"
                }
                if (paid) {
                    submit(orderId)
                    return@launch
                }
                delay(2000)
            }
        }
    }

    private fun submit(orderId: String) {
        binding.providerStatus.text = getString(R.string.provider_submitting)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { BridgeClient.submitOrder(prefs, orderId) }
            }
            result.onSuccess { json ->
                submitted = true
                binding.providerStatus.text = getString(R.string.provider_paid)
                binding.providerOrderNumber.text =
                    getString(R.string.provider_order_number, json.optString("orderNumber"))
                binding.providerOrderNumber.visibility = android.view.View.VISIBLE
                val checkout = json.optString("checkoutUrl")
                if (checkout.isNotEmpty() && checkout != "null") {
                    binding.providerCheckoutLink.visibility = android.view.View.VISIBLE
                    binding.providerCheckoutLink.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkout)))
                    }
                }
                json.optString("note").ifEmpty { null }?.let {
                    binding.providerNote.text = it
                    binding.providerNote.visibility = android.view.View.VISIBLE
                }
                binding.providerQr.visibility = android.view.View.INVISIBLE
                binding.providerDone.visibility = android.view.View.VISIBLE
            }.onFailure {
                binding.providerStatus.text = getString(R.string.provider_failed, it.message)
                Toast.makeText(this@ProviderPaymentActivity, it.message, Toast.LENGTH_LONG).show()
                binding.providerDone.visibility = android.view.View.VISIBLE
            }
        }
    }

    companion object {
        const val EXTRA_BOLT11 = "bridge_extra_bolt11"
        const val EXTRA_AMOUNT_SATS = "bridge_extra_amount_sats"
        const val EXTRA_ORDER_ID = "bridge_extra_order_id"
        const val EXTRA_MODE = "bridge_extra_mode"
    }
}
