package org.orbitfs.common.util

import org.orbitfs.common.model.PilotAvatar
import kotlin.random.Random

object IdentityGenerator {
    private val ADJECTIVES = listOf(
        "Crimson", "Emerald", "Azure", "Golden", "Silent", "Rapid", "Shadow", 
        "Solar", "Lunar", "Nova", "Cosmic", "Stellar", "Iron", "Vortex", "Orbital",
        "Nebula", "Galactic", "Void", "Meteor", "Comet"
    )
    
    private val NOUNS = listOf(
        "Pilot", "Rover", "Seeker", "Warden", "Voyager", "Falcon", "Eagle", 
        "Ranger", "Sentry", "Hunter", "Ghost", "Specter", "Titan", "Orbit", "Rocket",
        "Pulsar", "Quasar", "Drifter", "Scout", "Pathfinder"
    )

    fun generateRandomName(): String {
        val adj = ADJECTIVES.random()
        val noun = NOUNS.random()
        val suffix = Random.nextInt(1000, 9999)
        return "$adj-$noun-$suffix"
    }

    fun generateNodeId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        // 12-character alphanumeric ID
        return (1..12).map { chars.random() }.joinToString("")
    }

    fun generateRandomAvatarId(): String {
        return PilotAvatar.ALL.random().id
    }
}
