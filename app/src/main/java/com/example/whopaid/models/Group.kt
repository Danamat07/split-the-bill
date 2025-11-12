package com.example.whopaid.models

data class Group(
    var id: String? = null,  // <--- trebuie var, nu val
    val name: String = "",
    val description: String = "",
    val adminUid: String = "",
    val members: List<String> = listOf(),
    var qrPayload: String? = null
)
