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

/**
 * Object containing keys used for accessing values in [android.content.SharedPreferences].
 */
object SharedPreferencesKeys {
  /** The name of the SharedPreferences file. */
  const val PREFS_NAME = "SampleAppPrefs"
  /** The key for storing the user ID. */
  const val KEY_USER_ID = "user_id"
  /** The key for storing the authentication token. */
  const val KEY_AUTH_TOKEN = "auth_token"
}
