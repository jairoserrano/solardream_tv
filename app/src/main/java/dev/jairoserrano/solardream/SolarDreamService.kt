package dev.jairoserrano.solardream

import android.service.dreams.DreamService
import dev.jairoserrano.solardream.ui.FlowView

class SolarDreamService : DreamService() {
    private var poller: EnergyPoller? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val view = FlowView(this)
        setContentView(view)
        poller = EnergyPoller(DtuSettings.host(this), view, forcedDriverId = DtuSettings.inverterType(this))
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        poller?.start()
    }

    override fun onDreamingStopped() {
        poller?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        poller?.stop()
        poller = null
        super.onDetachedFromWindow()
    }
}

