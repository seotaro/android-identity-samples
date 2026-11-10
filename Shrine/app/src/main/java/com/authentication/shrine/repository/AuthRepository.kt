/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.authentication.shrine.repository

import android.util.Log
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.RestoreCredential
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.authentication.shrine.GenericCredentialManagerResponse
import com.authentication.shrine.api.AuthApiService
import com.authentication.shrine.model.AuthError
import com.authentication.shrine.model.AuthResult
import com.authentication.shrine.model.CredmanResponse
import com.authentication.shrine.model.LoginUsernamePasswordRequest
import com.authentication.shrine.model.RegisterRequestRequestBody
import com.authentication.shrine.model.RegisterResponseRequestBody
import com.authentication.shrine.model.ResponseObject
import com.authentication.shrine.model.SignInResponseRequest
import com.authentication.shrine.utility.createCookieHeader
import com.authentication.shrine.utility.getJsonObject
import com.authentication.shrine.utility.getSessionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository class that handles authentication-related operations: password sign-in and the
 * restore key (Restore Credentials) used for zero-tap sign-in.
 *
 * @param authApiService The API service for interacting with the server.
 * @param dataStore The data store for storing user data.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val authApiService: AuthApiService,
) {

    // Companion object for constants and helper methods
    companion object {
        const val TAG = "AuthRepository"

        // Keys for the DataStore
        val USERNAME = stringPreferencesKey("username")
        val DISPLAYNAME = stringPreferencesKey("displayname")
        val SESSION_ID = stringPreferencesKey("session_id")
        val RESTORE_KEY_CREDENTIAL_ID = stringPreferencesKey("restore_key_credential_id")

        // Value for restore credential AuthApiService parameter
        const val RESTORE_KEY_TYPE_PARAMETER = "rc"

        // WebAuthn credential type, used for restore keys as well.
        private const val PUBLIC_KEY_CREDENTIAL_TYPE = "public-key"

        suspend fun <T> DataStore<Preferences>.read(key: Preferences.Key<T>): T? {
            return data.map { it[key] }.first()
        }
    }

    /**
     * Signs in with a username and password. The server creates the account if it doesn't exist,
     * so this is used for both sign-up and sign-in.
     *
     * @param username The username to send.
     * @param password The password to send.
     * @return [AuthResult.Success] if the login was successful.
     */
    suspend fun login(username: String, password: String): AuthResult<Unit> {
        return try {
            val response = authApiService.loginWithUsernamePassword(LoginUsernamePasswordRequest(username, password))
            if (response.isSuccessful) {
                dataStore.edit { prefs ->
                    prefs[USERNAME] = response.body()?.username.orEmpty()
                    prefs[DISPLAYNAME] = response.body()?.displayName.orEmpty()
                    response.getSessionId()?.also {
                        prefs[SESSION_ID] = it
                    }
                }
                AuthResult.Success(Unit)
            } else {
                if (response.code() == 401) {
                    signOut()
                }
                AuthResult.Failure(
                    AuthError.ServerError(
                        response.errorBody()?.let { parseResponseError(it) } ?: response.message(),
                    ),
                )
            }
        } catch (e: IOException) {
            AuthResult.Failure(AuthError.NetworkError)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.Unknown(e.message))
        }
    }

    /**
     * Clears all the sign-in information.
     */
    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs.remove(USERNAME)
            prefs.remove(DISPLAYNAME)
            prefs.remove(SESSION_ID)
            prefs.remove(RESTORE_KEY_CREDENTIAL_ID)
        }
    }

    /**
     * Starts to register a restore key with the server.
     *
     * @return The public key credential creation options for the restore key.
     */
    suspend fun registerRestoreKeyRequest(): AuthResult<JSONObject> {
        return try {
            val sessionId = dataStore.read(SESSION_ID)
            if (!sessionId.isNullOrEmpty()) {
                val response = authApiService.registerRequest(
                    cookie = sessionId.createCookieHeader(),
                    requestBody = RegisterRequestRequestBody(),
                )
                if (response.isSuccessful) {
                    dataStore.edit { prefs ->
                        response.getSessionId()?.also {
                            prefs[SESSION_ID] = it
                        }
                    }
                    AuthResult.Success(response.getJsonObject())
                } else {
                    if (response.code() == 401) {
                        signOut()
                    }
                    AuthResult.Failure(
                        AuthError.ServerError(
                            response.errorBody()?.let { parseResponseError(it) }
                                ?: response.message(),
                        ),
                    )
                }
            } else {
                AuthResult.Failure(AuthError.Unknown(null))
            }
        } catch (e: IOException) {
            AuthResult.Failure(AuthError.NetworkError)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.Unknown(e.message))
        }
    }

    /**
     * Finishes registering a restore key with the server. This should only be called after
     * a call to [registerRestoreKeyRequest] and the creation of the restore key on the device.
     *
     * @param credentialResponse The response from Credential Manager's restore key creation.
     * @return [AuthResult.Success] if the registration was successful.
     */
    suspend fun registerRestoreKeyResponse(
        credentialResponse: CreateRestoreCredentialResponse,
    ): AuthResult<Unit> {
        return try {
            val registrationResponseJson = JSONObject(credentialResponse.responseJson)
            val rawId = registrationResponseJson.getString("rawId")
            val response = registrationResponseJson.getJSONObject("response")
            val sessionId = dataStore.read(SESSION_ID)
            if (!sessionId.isNullOrBlank()) {
                val apiResult = authApiService.registerResponse(
                    cookie = sessionId.createCookieHeader(),
                    type = RESTORE_KEY_TYPE_PARAMETER,
                    requestBody = RegisterResponseRequestBody(
                        id = rawId,
                        type = PUBLIC_KEY_CREDENTIAL_TYPE,
                        rawId = rawId,
                        response = CredmanResponse(
                            clientDataJSON = response.getString("clientDataJSON"),
                            attestationObject = response.getString("attestationObject"),
                        ),
                    ),
                )
                if (apiResult.isSuccessful) {
                    dataStore.edit { prefs ->
                        prefs[RESTORE_KEY_CREDENTIAL_ID] = rawId
                        apiResult.getSessionId()?.also {
                            prefs[SESSION_ID] = it
                        }
                    }
                    AuthResult.Success(Unit)
                } else {
                    if (apiResult.code() == 401) {
                        signOut()
                    }
                    AuthResult.Failure(AuthError.ServerError(parseResponseError(apiResult.errorBody()!!)))
                }
            } else {
                AuthResult.Failure(AuthError.Unknown(null))
            }
        } catch (e: IOException) {
            AuthResult.Failure(AuthError.NetworkError)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.Unknown(e.message))
        }
    }

    /**
     * Creates a restore key on the device and registers it with the server, so that the user is
     * signed in automatically after restoring the app on a new device (zero-tap sign-in).
     *
     * @param createRestoreKeyOnCredMan Creates the restore key with Credential Manager from the
     * creation options returned by the server.
     * @return True if the restore key was created and registered, false otherwise.
     */
    suspend fun createRestoreKey(
        createRestoreKeyOnCredMan: suspend (JSONObject) -> GenericCredentialManagerResponse,
    ): Boolean {
        val request = registerRestoreKeyRequest()
        if (request !is AuthResult.Success) {
            Log.e(TAG, "Error requesting restore key creation options.")
            return false
        }
        val created = createRestoreKeyOnCredMan(request.data)
        val response = (created as? GenericCredentialManagerResponse.CreateCredentialSuccess)
            ?.createCredentialResponse as? CreateRestoreCredentialResponse
        if (response == null) {
            Log.e(TAG, "Error creating restore key: $created")
            return false
        }
        return registerRestoreKeyResponse(response) is AuthResult.Success
    }

    /**
     * Starts to sign in with a restore key by requesting a challenge from the server.
     *
     * @return The public key credential request options.
     */
    suspend fun signInRequest(): AuthResult<JSONObject> {
        return try {
            val response = authApiService.signInRequest()
            if (response.isSuccessful) {
                dataStore.edit { prefs ->
                    response.getSessionId()?.also {
                        prefs[SESSION_ID] = it
                    }
                }
                AuthResult.Success(response.getJsonObject())
            } else {
                if (response.code() == 401) {
                    signOut()
                }
                AuthResult.Failure(
                    AuthError.ServerError(
                        response.errorBody()?.let { parseResponseError(it) } ?: response.message(),
                    ),
                )
            }
        } catch (e: IOException) {
            AuthResult.Failure(AuthError.NetworkError)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.Unknown(e.message))
        }
    }

    /**
     * Finishes signing in with a restore key. This should only be called after a call to
     * [signInRequest] and the retrieval of the restore key from Credential Manager.
     *
     * @param credentialResponse The response from Credential Manager containing the restore key.
     * @return [AuthResult.Success] if the sign-in was successful.
     */
    suspend fun signInWithRestoreKeyResponse(credentialResponse: GetCredentialResponse): AuthResult<Unit> {
        return try {
            val credential = credentialResponse.credential as? RestoreCredential
                ?: return AuthResult.Failure(AuthError.Unknown("Not a restore credential"))
            val signInResponseJSON = JSONObject(credential.authenticationResponseJson)
            val response = signInResponseJSON.getJSONObject("response")
            val credentialId = signInResponseJSON.getString("rawId")
            val sessionId = dataStore.read(SESSION_ID)
            if (sessionId.isNullOrBlank()) {
                return AuthResult.Failure(AuthError.Unknown(null))
            }

            val apiResult = authApiService.signInResponse(
                cookie = sessionId.createCookieHeader(),
                requestBody = SignInResponseRequest(
                    id = credentialId,
                    type = PUBLIC_KEY_CREDENTIAL_TYPE,
                    rawId = credentialId,
                    response = ResponseObject(
                        clientDataJSON = response.getString("clientDataJSON"),
                        authenticatorData = response.getString("authenticatorData"),
                        signature = response.getString("signature"),
                        userHandle = response.getString("userHandle"),
                    ),
                ),
            )
            if (apiResult.isSuccessful) {
                dataStore.edit { prefs ->
                    // The restore key used for this sign-in is the one to delete on sign-out.
                    prefs[RESTORE_KEY_CREDENTIAL_ID] = credentialId
                    apiResult.getSessionId()?.also {
                        prefs[SESSION_ID] = it
                    }
                    prefs[USERNAME] = apiResult.body()?.username.orEmpty()
                    prefs[DISPLAYNAME] = apiResult.body()?.displayName.orEmpty()
                }
                AuthResult.Success(Unit)
            } else {
                if (apiResult.code() == 401) {
                    signOut()
                }
                AuthResult.Failure(AuthError.ServerError(parseResponseError(apiResult.errorBody()!!)))
            }
        } catch (e: IOException) {
            AuthResult.Failure(AuthError.NetworkError)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.Unknown(e.message))
        }
    }

    /**
     * Checks if a session ID is stored, i.e. the user is (or was) signed in.
     *
     * @return True if a session ID is stored, false otherwise.
     */
    suspend fun isSignedIn(): Boolean {
        return !dataStore.read(SESSION_ID).isNullOrBlank()
    }

    /**
     * Checks if session id is valid with server. Currently makes a getKeys() request
     *
     * @return [AuthResult.Success] if the session id is valid.
     */
    suspend fun isSessionIdValid(): AuthResult<Unit> {
        return try {
            val sessionId = dataStore.read(SESSION_ID)
            if (!sessionId.isNullOrBlank()) {
                val apiResult = authApiService.getKeys(
                    cookie = sessionId.createCookieHeader(),
                )
                if (apiResult.isSuccessful) {
                    AuthResult.Success(Unit)
                } else {
                    AuthResult.Failure(AuthError.InvalidCredentials)
                }
            } else {
                AuthResult.Failure(AuthError.Unknown(null))
            }
        } catch (e: IOException) {
            AuthResult.Failure(AuthError.NetworkError)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.Unknown(e.message))
        }
    }

    /**
     * Clears the stored session ID from the data store asynchronously.
     */
    suspend fun clearSessionIdFromDataStore() {
        dataStore.edit { prefs ->
            prefs.remove(SESSION_ID)
        }
    }

    /**
     * Retrieves the stored username asynchronously.
     *
     * @return The stored username as a [String]. Returns an empty string if no username is found.
     */
    suspend fun getUsername(): String {
        return dataStore.read(USERNAME).orEmpty()
    }

    /**
     * Deletes the restore key from the server.
     *
     * @return True if the deletion was successful, false otherwise.
     */
    suspend fun deleteRestoreKeyFromServer(): Boolean {
        val sessionId = dataStore.read(SESSION_ID)
        val credentialId = dataStore.read(RESTORE_KEY_CREDENTIAL_ID)
        try {
            if (!sessionId.isNullOrEmpty() && !credentialId.isNullOrEmpty()) {
                val response = authApiService.deleteCredential(
                    cookie = sessionId.createCookieHeader(),
                    credentialId = credentialId,
                )
                if (response.isSuccessful) {
                    return true
                } else if (response.code() == 401) {
                    signOut()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot call deleteRestoreKey", e)
        }
        return false
    }

    /**
     * Parses an okhttp Response error message into a string.
     */
    fun parseResponseError(errorBody: ResponseBody): String {
        return try {
            val errorString = errorBody.string()
            val jsonObject = JSONObject(errorString)
            jsonObject.getString("error")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response error body", e)
            "A server error occurred."
        }
    }
}
