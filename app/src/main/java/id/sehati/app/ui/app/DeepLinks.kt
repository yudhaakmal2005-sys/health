package id.sehati.app.ui.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Rute tujuan dari notifikasi (mis. "medications"); dikonsumsi sekali oleh navigasi warga. */
object DeepLinks {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()
    fun open(route: String?) { if (!route.isNullOrBlank()) _route.value = route }
    fun consume() { _route.value = null }
}
