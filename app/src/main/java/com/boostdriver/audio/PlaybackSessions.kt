package com.boostdriver.audio

internal class PlaybackSessions(private val capacity: Int = 8) {
    private val owners = linkedMapOf<Int, String>()
    val ids: Set<Int> get() = owners.keys.toSet()

    // The player-provided package is only a matching hint, not proof of identity.
    fun open(id: Int, owner: String?): Boolean {
        if (id <= 0 || owner.isNullOrBlank() || owner.length > 255 || id in owners || owners.size >= capacity) return false
        owners[id] = owner
        return true
    }

    fun close(id: Int, owner: String?): Boolean =
        owner != null && owners[id] == owner && owners.remove(id) != null

    fun clear() = owners.clear()
}
