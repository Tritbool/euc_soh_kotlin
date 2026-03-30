/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eucsoh.android.data.model

import android.net.Uri

/**
 * Represents an identified EUC wheel with its associated log files.
 */
data class WheelIdentity(
    val macAddress: String,              // "E9:A8:39:04:B4:8C"
    val displayName: String,             // "P6-50009559" or MAC if no name
    val csvFiles: Set<Uri>,             // List of CSV files for this wheel
    val manufacturer: String? = null,     // "Inmotion"
    val model: String? = null,           // "P6"
    val serialNumber: String? = null,    // "A14219A150009559"
    val source: WheelDataSource = WheelDataSource.UNKNOWN,
    val userAlias: String? = null,
){
    // Nom à afficher dans l'UI et les graphiques
    val effectiveName: String
        get() = displayName.takeIf { it != macAddress }   // vrai nom dans les données
            ?: userAlias                                    // alias utilisateur
            ?: macAddress                                   // fallback brut
}

/**
 * Source from which wheel data was detected.
 */
enum class WheelDataSource {
    WHEELLOG,      // From Download/WheelLog/MAC_ADDRESS/ folders
    EUC_WORLD,     // From Download/EUC World/EUC Data Logs/
    MANUAL,        // User-selected folder
    MIX,
    UNKNOWN
}


