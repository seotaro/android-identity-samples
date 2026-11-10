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
 * ViewModel for user registration with a password. Uses [AuthRepository] to interact with the
 * authentication backend
 *
 * @param repository The authentication repository.
 */
@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    /**
     * UI state for the registration screen.
     */
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Registers a new user with a password.
     *
     * @param username The username of the new user.
     * @param password The password of the new user.
     * @param onSuccess Lambda to be invoked when the registration is successful.
     */
    fun onPasswordRegister(
        username: String,
        password: String,
        onSuccess: suspend () -> Unit,
    ) {
        _uiState.update { it.copy(isLoading = true) }

        if (username.isNotEmpty() && password.isNotEmpty()) {
            viewModelScope.launch {
                when (val result = repository.login(username, password)) {
                    is AuthResult.Success -> {
                        onSuccess()
                        _uiState.update {
                            it.copy(
                                isSuccess = true,
                                isLoading = false,
                            )
                        }
                    }

                    is AuthResult.Failure -> {
                        val (messageResId, errorMessage) = result.error.toMessage()
                        _uiState.update {
                            it.copy(
                                messageResourceId = messageResId,
                                isLoading = false,
                                errorMessage = errorMessage,
                            )
                        }
                    }
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    messageResourceId = R.string.enter_valid_username_and_password,
                    isLoading = false,
                )
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
 * Represents the UI state for the registration screen.
 *
 * @param isLoading Indicates whether a registration operation is in progress.
 * @param isSuccess Indicates whether the registration was successful.
 * @param messageResourceId The resource ID of a message to display to the user.
 */
data class RegisterUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    @StringRes val messageResourceId: Int = R.string.empty_string,
    val errorMessage: String? = null,
)
