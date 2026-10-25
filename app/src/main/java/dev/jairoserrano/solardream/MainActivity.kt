package dev.jairoserrano.solardream

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import dev.jairoserrano.solardream.driver.DiscoveredInverter
import dev.jairoserrano.solardream.driver.SubnetScanner
import dev.jairoserrano.solardream.ui.FlowView
import kotlin.concurrent.thread

/**
 * Actividad principal compatible con el lanzador de Android TV (Leanback) y dispositivos estándar.
 *
 * Muestra la visualización en tiempo real a pantalla completa y permite configurar
 * la IP del DTU mediante el mando a distancia (pulsando OK o Menú), o escanear la red local.
 */
class MainActivity : Activity() {
    private lateinit var flowView: FlowView
    private var poller: EnergyPoller? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val demoScene = intent.getIntExtra("demo_scene", 0)
        if (demoScene > 0) {
            applyDemoScene(demoScene)
            return
        }

        flowView = FlowView(this)
        setContentView(flowView)

        flowView.setOnClickListener {
            showSettingsDialog()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val demoScene = intent?.getIntExtra("demo_scene", 0) ?: 0
        if (demoScene > 0) {
            poller?.stop()
            poller = null
            applyDemoScene(demoScene)
        }
    }

    private fun applyDemoScene(demoScene: Int) {
        val isEnglish = demoScene in 7..8
        val ctx = if (isEnglish) {
            val conf = resources.configuration
            conf.setLocale(java.util.Locale.ENGLISH)
            createConfigurationContext(conf)
        } else this
        flowView = FlowView(ctx)
        setContentView(flowView)
        val state = dev.jairoserrano.solardream.ui.DemoScenes.getScene(demoScene, isEnglish)
        flowView.render(state)
        if (demoScene == 5) {
            mainHandler.postDelayed({ showSettingsDialog() }, 400)
        }
    }

    override fun onStart() {
        super.onStart()
        if (intent.getIntExtra("demo_scene", 0) > 0) return
        startPoller()
    }

    override fun onStop() {
        poller?.stop()
        poller = null
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_MENU
        ) {
            showSettingsDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startPoller() {
        poller?.stop()
        val host = DtuSettings.host(this)
        val type = DtuSettings.inverterType(this)
        poller = EnergyPoller(host, flowView, forcedDriverId = type).also { it.start() }
    }

    private fun showSettingsDialog() {
        val currentHost = DtuSettings.host(this)
        val input = EditText(this).apply {
            setText(currentHost)
            hint = "192.168.1.100"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSelection(text.length)
            setPadding(48, 36, 48, 36)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_settings_title))
            .setMessage(getString(R.string.dialog_dtu_ip_message))
            .setView(input)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                val newHost = input.text.toString().trim()
                if (newHost.isNotBlank()) {
                    DtuSettings.setHost(this, newHost)
                    DtuSettings.setInverterType(this, DtuSettings.TYPE_AUTO)
                    Toast.makeText(this, getString(R.string.toast_ip_saved, newHost), Toast.LENGTH_SHORT).show()
                    startPoller()
                }
            }
            .setNeutralButton(getString(R.string.dialog_scan_network)) { _, _ ->
                scanNetworkForInverters()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun scanNetworkForInverters() {
        val progress = ProgressDialog(this).apply {
            setTitle(getString(R.string.dialog_scan_network))
            setMessage(getString(R.string.dialog_scanning_network))
            setCancelable(false)
            show()
        }

        thread {
            val scanner = SubnetScanner()
            val prefix = scanner.getLocalSubnetPrefix(DtuSettings.host(this))
            val discovered = scanner.scanSubnet(prefix)

            mainHandler.post {
                progress.dismiss()
                if (discovered.isEmpty()) {
                    Toast.makeText(this, getString(R.string.dialog_no_inverters_found), Toast.LENGTH_LONG).show()
                } else {
                    showDiscoveredInvertersDialog(discovered)
                }
            }
        }
    }

    private fun showDiscoveredInvertersDialog(discovered: List<DiscoveredInverter>) {
        val items = discovered.map { "${it.displayName} (${it.host}:${it.port})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_inverters_found))
            .setItems(items) { _, which ->
                val selected = discovered[which]
                DtuSettings.setHost(this, selected.host)
                DtuSettings.setInverterType(this, selected.driverId)
                Toast.makeText(this, getString(R.string.toast_ip_saved, "${selected.displayName} @ ${selected.host}"), Toast.LENGTH_SHORT).show()
                startPoller()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }
}
