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
package com.authentication.shrine.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authentication.shrine.GenericCredentialManagerResponse
import com.authentication.shrine.R
import com.authentication.shrine.model.AuthError
import com.authentication.shrine.model.AuthResult
import com.authentication.shrine.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * A ViewModel that handles authentication-related operations.
 *
 * This ViewModel is responsible for:
 *
 * - Signing in the user with a username and password.
 * - Signing in the user with a restore key without any interaction (zero-tap sign-in).
 * - Creating a restore key after the user signs in.
 *
 * The ViewModel maintains an [AuthenticationUiState] object that represents the current UI state of the
 * authentication screen.
 */
@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    /**
     * The UI state of the authentication screen.
     */
    private val _uiState = MutableStateFlow(AuthenticationUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Signs in with a username and password typed in by the user.
     *
     * @param username The username (email address).
     * @param password The password.
     * @param onSuccess Lambda that handles actions on successful sign-in
     */
    fun signInWithPassword(
        username: String,
        password: String,
        onSuccess: suspend () -> Unit,
    ) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update {
                AuthenticationUiState(messageResourceId = R.string.enter_valid_username_and_password)
            }
            return
        }
        _uiState.update { AuthenticationUiState(isLoading = true) }
        viewModelScope.launch {
            login(username, password, onSuccess)
        }
    }

    private suspend fun login(
        username: String,
        password: String,
        onSuccess: suspend () -> Unit,
    ) {
        when (val result = repository.login(username, password)) {
            is AuthResult.Success -> {
                onSuccess()
                _uiState.update { AuthenticationUiState() }
            }

            is AuthResult.Failure -> {
                repository.clearSessionIdFromDataStore()
                val (messageResId, errorMessage) = result.error.toMessage()
                _uiState.update {
                    AuthenticationUiState(
                        messageResourceId = messageResId,
                        errorMessage = errorMessage,
                    )
                }
            }
        }
    }

    /**
     * Checks for a stored restore key and signs in with it if found (zero-tap sign-in).
     *
     * This does nothing if the user is already signed in. If no restore key is found, the user
     * stays on the authentication screen.
     *
     * @param getRestoreKey Retrieves the restore key from Credential Manager, using the sign-in
     * challenge returned by the server.
     * @param onSuccess Lambda that handles actions on successful sign-in.
     */
    fun checkForStoredRestoreKey(
        getRestoreKey: suspend (JSONObject) -> GenericCredentialManagerResponse,
        onSuccess: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            if (repository.isSignedIn()) return@launch

            val request = repository.signInRequest()
            if (request !is AuthResult.Success) {
                repository.clearSessionIdFromDataStore()
                return@launch
            }
            val restoreKeyResponse = getRestoreKey(request.data)
            if (restoreKeyResponse !is GenericCredentialManagerResponse.GetCredentialSuccess) {
                repository.clearSessionIdFromDataStore()
                return@launch
            }

            _uiState.update { AuthenticationUiState(isLoading = true) }
            when (repository.signInWithRestoreKeyResponse(restoreKeyResponse.getCredentialResponse)) {
                is AuthResult.Success -> {
                    onSuccess()
                    _uiState.update { AuthenticationUiState() }
                }

                is AuthResult.Failure -> {
                    repository.clearSessionIdFromDataStore()
                    _uiState.update {
                        AuthenticationUiState(messageResourceId = R.string.error_invalid_credentials)
                    }
                }
            }
        }
    }

    /**
     * Creates a restore key and registers it with the server.
     *
     * @param createRestoreKeyOnCredMan Creates the restore key with Credential Manager.
     * @return True if the restore key was created, false otherwise.
     */
    suspend fun createRestoreKey(
        createRestoreKeyOnCredMan: suspend (JSONObject) -> GenericCredentialManagerResponse,
    ): Boolean = repository.createRestoreKey(createRestoreKeyOnCredMan)
}

/**
 * Maps an [AuthError] to a message resource ID and an optional detail message.
 */
internal fun AuthError.toMessage(): Pair<Int, String?> = when (this) {
    is AuthError.NetworkError -> R.string.error_network to null
    is AuthError.UserAlreadyExists -> R.string.error_user_exists to null
    is AuthError.InvalidCredentials -> R.string.error_invalid_credentials to null
    is AuthError.ServerError -> R.string.error_server to message
    is AuthError.Unknown -> R.string.error_unknown to message
}

/**
 * Data class that stores the data of the authentication screen.
 *
 * @param isLoading Indicates whether a sign-in operation is in progress.
 * @param messageResourceId The resource ID of a message to display to the user.
 * @param errorMessage An additional error message to display after the message.
 */
data class AuthenticationUiState(
    val isLoading: Boolean = false,
    @StringRes val messageResourceId: Int = R.string.empty_string,
    val errorMessage: String? = null,
)
