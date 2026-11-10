/*
 * Copyright 2025 The Android Open Source Project
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
package com.authentication.shrine.api

import com.authentication.shrine.model.GenericAuthResponse
import com.authentication.shrine.model.LoginUsernamePasswordRequest
import com.authentication.shrine.model.RegisterRequestRequestBody
import com.authentication.shrine.model.RegisterRequestResponse
import com.authentication.shrine.model.RegisterResponseRequestBody
import com.authentication.shrine.model.SignInRequestResponse
import com.authentication.shrine.model.SignInResponseRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Interface defining the API endpoints for authentication and WebAuthn operations.
 * This interface is intended to be used with Retrofit for making network requests.
 *
 * Restore keys are registered and verified by the server through the same WebAuthn endpoints
 * as passkeys.
 */
interface AuthApiService {

    /**
     * Logs in with the username and password. The server creates the account if it doesn't exist.
     *
     * @param usernamePasswordRequest The request body containing the username and password.
     * @return A Retrofit {@link Response} wrapping a {@link GenericAuthResponse},
     *         indicating the success or failure of the operation.
     */
    @POST("auth/username-password")
    suspend fun loginWithUsernamePassword(
        @Body usernamePasswordRequest: LoginUsernamePasswordRequest,
    ): Response<GenericAuthResponse>

    /**
     * Requests the options (a challenge) to register a new restore key.
     *
     * @param cookie The session cookie for authentication.
     * @param requestBody The request body containing the authenticator selection.
     * @return A Retrofit {@link Response} wrapping a {@link RegisterRequestResponse}.
     */
    @POST("webauthn/registerRequest")
    suspend fun registerRequest(
        @Header("Cookie") cookie: String,
        @Body requestBody: RegisterRequestRequestBody,
    ): Response<RegisterRequestResponse>

    /**
     * Sends the client's response to a WebAuthn registration challenge back to the server
     * to complete the restore key registration.
     *
     * @param cookie The session cookie for authentication.
     * @param type The type of credential. `rc` marks the credential as a restore key.
     * @param requestBody The request body containing the client's attestation response
     *                    to the registration challenge.
     * @return A Retrofit {@link Response} wrapping a {@link GenericAuthResponse},
     *         indicating the success or failure of the registration.
     */
    @POST("webauthn/registerResponse")
    suspend fun registerResponse(
        @Header("Cookie") cookie: String,
        @Query("type") type: String?,
        @Body requestBody: RegisterResponseRequestBody,
    ): Response<GenericAuthResponse>

    /**
     * Initiates a WebAuthn sign-in ceremony by requesting assertion options
     * (a challenge) from the server.
     *
     * @return A Retrofit {@link Response} wrapping a {@link SignInRequestResponse},
     *         which contains the challenge and options for the WebAuthn sign-in.
     */
    @POST("webauthn/signinRequest")
    suspend fun signInRequest(): Response<SignInRequestResponse>

    /**
     * Sends the client's response to a WebAuthn sign-in challenge (assertion) back
     * to the server to complete the authentication.
     *
     * @param cookie The session cookie that might have been established or is being verified.
     * @param requestBody The request body containing the client's assertion response
     *                    to the sign-in challenge.
     * @return A Retrofit {@link Response} wrapping a {@link GenericAuthResponse},
     *         indicating the success or failure of the WebAuthn sign-in.
     */
    @POST("webauthn/signinResponse")
    suspend fun signInResponse(
        @Header("Cookie") cookie: String,
        @Body requestBody: SignInResponseRequest,
    ): Response<GenericAuthResponse>

    /**
     * Retrieves the credentials registered for the authenticated user. Only used to check
     * whether the session is still valid.
     *
     * @param cookie The session cookie for authentication.
     */
    @POST("webauthn/getKeys")
    suspend fun getKeys(
        @Header("Cookie") cookie: String,
    ): Response<ResponseBody>

    /**
     * Deletes a credential (the restore key) from the authentication server.
     */
    @POST("webauthn/removeKey")
    suspend fun deleteCredential(
        @Header("Cookie") cookie: String,
        @Query("credId") credentialId: String,
    ): Response<GenericAuthResponse>
}
