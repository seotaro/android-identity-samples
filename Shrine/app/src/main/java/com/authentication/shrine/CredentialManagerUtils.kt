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
package com.authentication.shrine

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.ClearCredentialStateRequest.Companion.TYPE_CLEAR_RESTORE_CREDENTIAL
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import org.json.JSONObject
import javax.inject.Inject

/**
 * A utility class that provides methods for interacting with the credential manager.
 *
 * Only restore keys (Restore Credentials) are used in this app. Passwords are typed in by the
 * user and are not saved to the password manager.
 *
 * @param credentialManager Instance of [CredentialManager]
 */
class CredentialManagerUtils @Inject constructor(
    private val credentialManager: CredentialManager,
) {

    /**
     * Creates a restore key using the Credential Manager API.
     *
     * @param requestResult A [JSONObject] containing the data required for creating the restore
     * credential. This data is used to create a [CreateRestoreCredentialRequest]
     *
     * @param context The Android Context used for checking eligibility and interacting
     * with the Credential Manager API.
     *
     * @return A [GenericCredentialManagerResponse] indicating the result of the operation
     */
    suspend fun createRestoreKey(
        requestResult: JSONObject,
        context: Context,
    ): GenericCredentialManagerResponse {
        val passkeysEligibility = PasskeysEligibility.isPasskeySupported(context)
        val credentialResponse: CreateRestoreCredentialResponse

        if (!passkeysEligibility.isEligible) {
            return GenericCredentialManagerResponse.Error(errorMessage = passkeysEligibility.reason)
        }

        try {
            credentialResponse = try {
                credentialManager.createCredential(
                    context,
                    CreateRestoreCredentialRequest(requestResult.toString()),
                ) as CreateRestoreCredentialResponse
            } catch (e: E2eeUnavailableException) {
                // The device does not meet the cloud backup requirements, so retry locally.
                // https://developer.android.com/identity/sign-in/restore-credentials#previous-device
                credentialManager.createCredential(
                    context,
                    CreateRestoreCredentialRequest(
                        requestResult.toString(),
                        isCloudBackupEnabled = false,
                    ),
                ) as CreateRestoreCredentialResponse
            }
        } catch (e: Exception) {
            return GenericCredentialManagerResponse.Error(errorMessage = e.message ?: "")
        }
        return GenericCredentialManagerResponse.CreateCredentialSuccess(createCredentialResponse = credentialResponse)
    }

    /**
     * Retrieves the restore key using the Credential Manager API.
     *
     * @param authenticationJson The JSON object containing authentication information.
     * @param context The application context.
     * @return A [GenericCredentialManagerResponse] object indicating success or failure.
     */
    suspend fun getRestoreKey(
        authenticationJson: JSONObject,
        context: Context,
    ): GenericCredentialManagerResponse {
        val passkeysEligibility = PasskeysEligibility.isPasskeySupported(context)
        if (!passkeysEligibility.isEligible) {
            return GenericCredentialManagerResponse.Error(errorMessage = passkeysEligibility.reason)
        }

        val options = GetRestoreCredentialOption(authenticationJson.toString())
        val getRestoreKeyRequest = GetCredentialRequest(listOf(options))
        val result: GetCredentialResponse?
        try {
            result = credentialManager.getCredential(
                context,
                getRestoreKeyRequest,
            )
        } catch (e: Exception) {
            return GenericCredentialManagerResponse.Error(errorMessage = e.message ?: "")
        }
        return GenericCredentialManagerResponse.GetCredentialSuccess(result)
    }

    /**
     * Deletes the restore key using the Credential Manager API.
     */
    suspend fun deleteRestoreKey() {
        val clearRequest = ClearCredentialStateRequest(requestType = TYPE_CLEAR_RESTORE_CREDENTIAL)
        credentialManager.clearCredentialState(clearRequest)
    }
}

sealed class GenericCredentialManagerResponse {
    data class GetCredentialSuccess(
        val getCredentialResponse: GetCredentialResponse,
    ) : GenericCredentialManagerResponse()

    data class CreateCredentialSuccess(
        val createCredentialResponse: CreateCredentialResponse,
    ) : GenericCredentialManagerResponse()

    data class Error(
        val errorMessage: String,
    ) : GenericCredentialManagerResponse()
}
