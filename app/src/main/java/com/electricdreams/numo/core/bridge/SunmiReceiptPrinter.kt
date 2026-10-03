package com.electricdreams.numo.core.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterException
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sunmi built-in printer receipt: binds the vendor print service on demand
 * and stays a silent no-op (available=false) on non-Sunmi devices — the UI
 * hides the print action when the service is not there.
 */
object SunmiReceiptPrinter {

    private const val TAG = "SunmiReceipt"
    private var service: SunmiPrinterService? = null
    private var hasPrinter: Boolean = false
    private var bindAttempted = false

    val available: Boolean get() = service != null && hasPrinter

    private val callback = object : InnerPrinterCallback() {
        override fun onConnected(sunmiPrinterService: SunmiPrinterService) {
            service = sunmiPrinterService
            hasPrinter = try {
                InnerPrinterManager.getInstance().hasPrinter(sunmiPrinterService)
            } catch (e: InnerPrinterException) {
                false
            }
            Log.d(TAG, "printer service connected, hasPrinter=$hasPrinter")
        }

        override fun onDisconnected() {
            service = null
            hasPrinter = false
        }
    }

    fun bind(context: Context) {
        if (bindAttempted) return
        bindAttempted = true
        try {
            val ok = InnerPrinterManager.getInstance().bindService(context.applicationContext, callback)
            if (!ok) Log.d(TAG, "no Sunmi print service on this device")
        } catch (e: InnerPrinterException) {
            Log.d(TAG, "Sunmi bind failed: ${e.message}")
        }
    }

    data class Receipt(
        val venueName: String,
        val items: List<Pair<String, Int>>,
        val totalEur: String,
        val orderNumber: String,
        val mode: String,
    )

    fun print(receipt: Receipt): Boolean {
        val s = service ?: return false
        if (!hasPrinter) return false
        return try {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            s.setAlignment(1, null)
            s.printTextWithFont(receipt.venueName + "\n", null, 26f, null)
            s.printTextWithFont("PAID via Lightning\n", null, 18f, null)
            s.printText("$ts\n\n", null)
            s.setAlignment(0, null)
            receipt.items.forEach { (name, qty) ->
                s.printText(String.format(Locale.US, "%dx  %s\n", qty, name), null)
            }
            s.printText("\nTOTAL: ${receipt.totalEur}\n", null)
            s.printText("Order number:\n", null)
            s.printTextWithFont(receipt.orderNumber + "\n", null, 32f, null)
            if (receipt.mode == "demo") s.printText("(DEMO order — no real venue)\n", null)
            s.printText("\nBurgermeister via Numo bridge\n", null)
            s.lineWrap(3, null)
            s.cutPaper(null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "print failed: ${e.message}", e)
            false
        }
    }

    @Suppress("unused")
    private val mainHandler = Handler(Looper.getMainLooper())
}
