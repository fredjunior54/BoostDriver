package com.boostdriver

// Used on the main thread by the activity, service, and receivers.
internal class BoostRequests {
    var wanted = false
        private set
    var generation = 0L
        private set

    fun enable(gain: Int): Long? {
        if (gain <= 0 || wanted) return null
        wanted = true
        return ++generation
    }

    fun update(): Long? = if (wanted) ++generation else null

    fun disable() {
        wanted = false
        ++generation
    }

    fun accepts(request: Long) = wanted && request == generation
}
