package com.volunteer.manager.models

import java.io.Serializable

data class Campaign(
    var id: String? = null,
    val title: String = "",
    val time: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val participants: HashMap<String, Boolean> = HashMap(),
    val favoriteBy: HashMap<String, Boolean> = HashMap(),
    val orgId: String = ""
) : Serializable
