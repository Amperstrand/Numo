package com.electricdreams.numo.feature.settings

import android.os.Bundle
import org.json.JSONArray
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.electricdreams.numo.R
import com.electricdreams.numo.core.bridge.BridgeClient
import com.electricdreams.numo.core.bridge.BridgePrefs
import com.electricdreams.numo.core.model.Item
import com.electricdreams.numo.core.model.PriceType
import com.electricdreams.numo.core.util.ItemManager
import com.electricdreams.numo.databinding.ActivityBridgeSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Bridge: base URL, provider on/off, DEMO/LIVE toggle (mirrors
 * the bridge's own /mode — one switch controls both), and a one-tap catalog
 * sync that persists provider menu items through ItemManager.
 */
class BridgeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBridgeSettingsBinding
    private lateinit var prefs: BridgePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBridgeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = BridgePrefs.getInstance(this)

        binding.bridgeUrlInput.setText(prefs.baseUrl)
        binding.bridgeEnabledSwitch.isChecked = prefs.enabled

        binding.bridgeEnabledSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            binding.bridgeModeLine.text = getString(
                if (checked) R.string.bridge_status_enabled else R.string.bridge_status_disabled
            )
        }

        binding.bridgeModeSwitch.setOnCheckedChangeListener { _, checked -> flipMode(checked) }

        binding.bridgeSyncButton.setOnClickListener {
            prefs.baseUrl = binding.bridgeUrlInput.text.toString().trim()
            binding.bridgeResult.text = getString(R.string.bridge_syncing)
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { BridgeClient.getMenu(prefs) }
                }
                result.onSuccess { json ->
                    val items = json.optJSONArray("items") ?: JSONArray()
                    val venue = json.optJSONObject("venue")?.optString("name") ?: "?"
                    val manager = ItemManager.getInstance(this@BridgeSettingsActivity)
                    manager.clearItems()
                    for (i in 0 until items.length()) {
                        val o = items.optJSONObject(i) ?: continue
                        manager.addItem(
                            Item(
                                id = o.optString("id"),
                                name = o.optString("name"),
                                description = o.optString("description").ifEmpty { null },
                                category = o.optString("category").ifEmpty { null },
                                sku = o.optString("sku").ifEmpty { null },
                                price = o.optDouble("price", 0.0),
                                priceSats = o.optLong("priceSats", 0L),
                                priceType = if (o.optString("priceType") == "SATS") PriceType.SATS else PriceType.FIAT,
                                vatEnabled = o.optBoolean("vatEnabled", false),
                                vatRate = o.optInt("vatRate", 0),
                            )
                        )
                    }
                    binding.bridgeResult.text =
                        getString(R.string.bridge_sync_done, items.length(), venue, json.optString("mode"))
                }.onFailure {
                    binding.bridgeResult.text = getString(R.string.bridge_sync_failed, it.message)
                }
            }
        }

        binding.bridgeModeLine.setOnClickListener {
            refreshMode()
        }
        refreshMode()
    }

    private fun refreshMode() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    prefs.baseUrl = binding.bridgeUrlInput.text.toString().trim()
                    BridgeClient.getMode(prefs)
                }
            }
            result.onSuccess { json ->
                val mode = json.optString("mode")
                binding.bridgeModeSwitch.setOnCheckedChangeListener(null)
                binding.bridgeModeSwitch.isChecked = mode == "live"
                binding.bridgeModeSwitch.setOnCheckedChangeListener { _, checked -> flipMode(checked) }
                binding.bridgeModeLine.text =
                    getString(R.string.bridge_mode_current, mode, json.optJSONArray("realOrderVenues")?.join(", "))
            }.onFailure {
                binding.bridgeModeLine.text = getString(R.string.bridge_status_offline)
            }
        }
    }

    private fun flipMode(toLive: Boolean) {
        val target = if (toLive) "live" else "demo"
        binding.bridgeModeSwitch.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { BridgeClient.postMode(prefs, target) }
            }
            binding.bridgeModeSwitch.isEnabled = true
            result.onSuccess { json ->
                val mode = json.optString("mode")
                binding.bridgeModeLine.text =
                    getString(R.string.bridge_mode_current, mode, json.optJSONArray("realOrderVenues")?.join(", "))
            }.onFailure {
                Toast.makeText(this@BridgeSettingsActivity, it.message, Toast.LENGTH_LONG).show()
                binding.bridgeModeSwitch.isChecked = !toLive
            }
        }
    }

    private fun JSONArray.join(sep: String): String =
        (0 until length()).joinToString(sep) { optString(it) }

    companion object {
        private const val TAG = "BridgeSettings"
    }
}
