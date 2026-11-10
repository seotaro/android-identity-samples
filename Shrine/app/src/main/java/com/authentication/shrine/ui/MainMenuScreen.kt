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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.authentication.shrine.CredentialManagerUtils
import com.authentication.shrine.R
import com.authentication.shrine.ui.common.LogoHeading
import com.authentication.shrine.ui.common.ShrineButton
import com.authentication.shrine.ui.theme.ShrineTheme
import com.authentication.shrine.ui.viewmodel.HomeViewModel

/**
 * Stateful composable function that displays the main menu screen.
 *
 * @param navigateToLogin Callback for navigating to the login screen.
 * @param viewModel The HomeViewModel that provides the UI state.
 * @param credentialManagerUtils The instance of [CredentialManagerUtils]
 */
@Composable
fun MainMenuScreen(
    navigateToLogin: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    credentialManagerUtils: CredentialManagerUtils,
) {
    val uiState = viewModel.uiState.collectAsState().value
    val onSignOut = {
        viewModel.signOut(
            deleteRestoreKey = credentialManagerUtils::deleteRestoreKey,
        )
    }

    MainMenuScreen(
        username = uiState.username,
        onSignOut = {
            onSignOut()
            navigateToLogin()
        },
        modifier = modifier,
    )
}

/**
 * Stateless composable function for the main menu screen. It shows the signed-in user and a
 * button to sign out.
 *
 * @param username The username of the signed-in user.
 * @param onSignOut Callback to sign out the user.
 * @param modifier Modifier to be applied to the composable.
 */
@Composable
fun MainMenuScreen(
    username: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(contentPadding)
                .fillMaxHeight()
                .padding(dimensionResource(R.dimen.padding_medium)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoHeading()

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.size_large)))

            Text(
                text = stringResource(R.string.signed_in_as, username),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_extra_large)))

            ShrineButton(
                onClick = onSignOut,
                buttonText = stringResource(R.string.sign_out),
                usePrimaryColor = false,
                modifier = Modifier.widthIn(min = 280.dp),
            )
        }
    }
}

/**
 * Generates a preview of the main menu screen.
 */
@Preview(showBackground = true)
@Composable
fun MainMenuScreenPreview() {
    ShrineTheme {
        MainMenuScreen(
            username = "user@example.com",
            onSignOut = { },
        )
    }
}
