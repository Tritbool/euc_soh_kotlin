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

package io.github.eucsoh.android.ui.onboarding

import android.content.Context

object OnboardingManager {

    const val PREFS_NAME = "onboarding_prefs"
    const val KEY_MAIN_SEEN = "main_onboarding_seen"
    const val KEY_RESULT_SEEN = "result_onboarding_seen"

    fun hasSeenMain(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MAIN_SEEN, false)
    }

    fun markMainSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MAIN_SEEN, true)
            .apply()
    }

    fun hasSeenResult(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESULT_SEEN, false)
    }

    fun markResultSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESULT_SEEN, true)
            .apply()
    }

    fun resetAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MAIN_SEEN, false)
            .putBoolean(KEY_RESULT_SEEN, false)
            .apply()
    }
}
