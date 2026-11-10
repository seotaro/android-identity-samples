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
package com.authentication.shrine.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.authentication.shrine.R
import com.authentication.shrine.ui.theme.ShrineTheme

/**
 * Email address and password input fields, with a toggle to show the password.
 *
 * @param email The email address.
 * @param onEmailChanged Callback for when the email address changes.
 * @param password The password.
 * @param onPasswordChange Callback for when the password changes.
 * @param passwordVisible Whether the password is currently visible.
 * @param onPasswordVisibilityToggle Callback for toggling the visibility of the password.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun EmailPasswordFields(
    email: String,
    onEmailChanged: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextField(
            modifier = Modifier.padding(top = dimensionResource(R.dimen.dimen_standard)),
            value = email,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = stringResource(R.string.email_icon),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            onValueChange = onEmailChanged,
            label = { Text(stringResource(R.string.email_address)) },
            placeholder = { Text(stringResource(R.string.email_address)) },
        )

        TextField(
            modifier = Modifier.padding(top = dimensionResource(R.dimen.dimen_standard)),
            value = password,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Password,
                    contentDescription = stringResource(R.string.password_icon),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.password)) },
            trailingIcon = {
                val image = if (passwordVisible) {
                    Icons.Filled.Visibility
                } else {
                    Icons.Filled.VisibilityOff
                }
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(imageVector = image, stringResource(R.string.password))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmailPasswordFieldsPreview() {
    ShrineTheme {
        EmailPasswordFields(
            email = "",
            onEmailChanged = { },
            password = "",
            onPasswordChange = { },
            passwordVisible = false,
            onPasswordVisibilityToggle = { },
        )
    }
}
