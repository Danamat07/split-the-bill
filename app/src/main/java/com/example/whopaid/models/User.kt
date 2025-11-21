package com.example.whopaid.models

/**
 * User model stored in Firestore.
 *
 * IMPORTANT:
 * - Nu folosește anotări @Entity pentru că Firebase Firestore nu le suportă.
 * - Trebuie să aibă constructor fără parametri.
 * - Toate proprietățile trebuie să fie var + nullable pentru Firestore.
 */

data class User(
    var uid: String = "",
    var name: String? = null,
    var email: String? = null,
    var phone: String? = null,

    // --- LOCATION SHARING ---
    var shareLocation: Boolean = false,      // utilizatorul permite partajarea locației?
    var locationLat: Double? = null,         // latitudine curentă
    var locationLng: Double? = null          // longitudine curentă
)
