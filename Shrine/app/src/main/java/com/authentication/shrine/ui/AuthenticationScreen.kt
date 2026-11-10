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
package com.authentication.shrine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.authentication.shrine.CredentialManagerUtils
import com.authentication.shrine.R
import com.authentication.shrine.ui.common.EmailPasswordFields
import com.authentication.shrine.ui.common.LogoHeading
import com.authentication.shrine.ui.common.ShrineButton
import com.authentication.shrine.ui.common.ShrineLoader
import com.authentication.shrine.ui.theme.ShrineTheme
import com.authentication.shrine.ui.viewmodel.AuthenticationUiState
import com.authentication.shrine.ui.viewmodel.AuthenticationViewModel

/**
 * Stateful composable function for Authentication screen.
 *
 * On first display, the user is signed in automatically if a restore key is found on the device
 * (zero-tap sign-in). Otherwise the user signs in with an email address and a password. A restore
 * key is created after every successful sign-in.
 *
 * @param navigateToHome Callback to navigate to the home screen.
 * @param viewModel The [AuthenticationViewModel] for this screen.
 * @param navigateToRegister Callback to navigate to the registration screen.
 * @param credentialManagerUtils The instance of [CredentialManagerUtils]
 */
@Composable
fun AuthenticationScreen(
    navigateToHome: () -> Unit,
    viewModel: AuthenticationViewModel,
    navigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    credentialManagerUtils: CredentialManagerUtils,
) {
    val uiState = viewModel.uiState.collectAsState().value
    var isFirstCheckForRestoreKey by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // Passing in the lambda / context to the VM
    val context = LocalContext.current
    val onSignInSuccess: suspend () -> Unit = {
        viewModel.createRestoreKey(
            createRestoreKeyOnCredMan = { createRestoreCredObject ->
                credentialManagerUtils.createRestoreKey(
                    context = context,
                    requestResult = createRestoreCredObject,
                )
            },
        )
        navigateToHome()
    }

    if (isFirstCheckForRestoreKey) {
        isFirstCheckForRestoreKey = false
        viewModel.checkForStoredRestoreKey(
            getRestoreKey = { requestResult ->
                credentialManagerUtils.getRestoreKey(requestResult, context)
            },
            onSuccess = { navigateToHome() },
        )
    }

    AuthenticationScreen(
        email = email,
        onEmailChanged = { email = it },
        password = password,
        onPasswordChange = { password = it },
        passwordVisible = passwordVisible,
        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
        onSignIn = {
            viewModel.signInWithPassword(
                username = email,
                password = password,
                onSuccess = onSignInSuccess,
            )
        },
        navigateToRegister = navigateToRegister,
        uiState = uiState,
        modifier = modifier,
    )
}

/**
 * Stateless composable function for the authentication screen.
 *
 * @param email The user's email address.
 * @param onEmailChanged Callback for when the email address changes.
 * @param password The user's password.
 * @param onPasswordChange Callback for when the password changes.
 * @param passwordVisible Whether the password is currently visible.
 * @param onPasswordVisibilityToggle Callback for toggling the visibility of the password.
 * @param onSignIn Callback to sign in with the typed in email address and password.
 * @param navigateToRegister Callback to navigate to the registration screen.
 * @param uiState The current UI state of the authentication screen.
 * @param modifier Modifier to be applied to the composable.
 */
@Composable
fun AuthenticationScreen(
    email: String,
    onEmailChanged: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    onSignIn: () -> Unit,
    navigateToRegister: () -> Unit,
    uiState: AuthenticationUiState,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(dimensionResource(R.dimen.padding_extra_large)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoHeading()

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_extra_large)))

            EmailPasswordFields(
                email = email,
                onEmailChanged = onEmailChanged,
                password = password,
                onPasswordChange = onPasswordChange,
                passwordVisible = passwordVisible,
                onPasswordVisibilityToggle = onPasswordVisibilityToggle,
            )

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_extra_large)))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ShrineButton(
                    onClick = onSignIn,
                    buttonText = stringResource(id = R.string.sign_in),
                    isButtonEnabled = !uiState.isLoading,
                    modifier = Modifier.widthIn(min = 280.dp),
                )

                ShrineButton(
                    onClick = navigateToRegister,
                    buttonText = stringResource(id = R.string.sign_up),
                    usePrimaryColor = false,
                    isButtonEnabled = !uiState.isLoading,
                    modifier = Modifier.widthIn(min = 280.dp),
                )
            }
        }

        if (uiState.isLoading) {
            ShrineLoader()
        }

        val snackbarMessage = if (uiState.messageResourceId != R.string.empty_string) {
            val baseMessage = stringResource(uiState.messageResourceId)
            if (uiState.errorMessage.isNullOrBlank()) baseMessage else "$baseMessage ${uiState.errorMessage}"
        } else {
            null
        }
        LaunchedEffect(uiState) {
            snackbarMessage?.let {
                snackbarHostState.showSnackbar(message = it)
            }
        }
    }
}

/**
 * Preview function for the authentication screen.
 */
@Preview(showSystemUi = true)
@Composable
fun AuthenticationScreenPreview() {
    ShrineTheme {
        AuthenticationScreen(
            email = "",
            onEmailChanged = { },
            password = "",
            onPasswordChange = { },
            passwordVisible = false,
            onPasswordVisibilityToggle = { },
            onSignIn = { },
            navigateToRegister = { },
            uiState = AuthenticationUiState(),
        )
    }
}
