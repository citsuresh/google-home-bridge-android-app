/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Manages reading and writing to [SharedPreferences].
 *
 * @property context The application context.
 */
@Singleton
class SharedPreferencesManager @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(SharedPreferencesKeys.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Saves a string value to SharedPreferences.
     *
     * @param key The key to associate with the value.
     * @param value The string value to save.
     */
    fun saveString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    /**
     * Retrieves a string value from SharedPreferences.
     *
     * @param key The key of the string value to retrieve.
     * @param defaultValue The default value to return if the key is not found.
     * @return The string value associated with the key, or [defaultValue] if not found.
     */
    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    /**
     * Saves an integer value to SharedPreferences.
     *
     * @param key The key to associate with the value.
     * @param value The integer value to save.
     */
    fun saveInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    /**
     * Retrieves an integer value from SharedPreferences.
     *
     * @param key The key of the integer value to retrieve.
     * @param defaultValue The default value to return if the key is not found.
     * @return The integer value associated with the key, or [defaultValue] if not found.
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    // Add other methods for different data types as needed
}
