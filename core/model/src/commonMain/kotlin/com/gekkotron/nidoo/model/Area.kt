package com.gekkotron.nidoo.model

import kotlinx.serialization.Serializable

const val UNASSIGNED_AREA = "Unassigned"

@Serializable
data class Area(
    val id: String,
    val name: String,
    val icon: String? = null,
    val order: Int? = null,
    /** Custom group card names by kind key (`cover`, `light`). No longer read by Home (it shows hub groups only); kept so stored areas round-trip intact. */
    val groupNames: Map<String, String> = emptyMap(),
    /** Kind keys (`cover`, `light`) of favorited room groups. No longer read by Home (it shows hub groups only); kept so stored areas round-trip intact. */
    val groupFavorites: Set<String> = emptySet(),
)

/** Retained payload of `nidoo/ui/_areas`. */
@Serializable
data class AreasDocument(val areas: List<Area> = emptyList())
