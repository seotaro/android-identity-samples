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
package com.authentication.shrine.ui.navigation

import androidx.navigation.NavHostController

/**
 * An enum class representing the different destinations in the Shrine app.
 */
enum class ShrineAppDestinations {
    MainMenuRoute,
    AuthRoute,
    RegisterPasswordRoute,
    NavHostRoute,
}

/**
 * A class that provides navigation actions for the Shrine app.
 * This controller will help decide where to navigate
 *
 * @param navController The [NavHostController] used for navigation.
 */
class ShrineNavActions(navController: NavHostController) {
    /**
     * Navigates to the home screen after the user signs in.
     */
    val navigateToHome: () -> Unit = {
        navController.navigate(ShrineAppDestinations.MainMenuRoute.name) {
            popUpTo(ShrineAppDestinations.NavHostRoute.name)
            launchSingleTop = true
        }
    }

    /**
     * Navigates to the login flow.
     */
    val navigateToLogin: () -> Unit = {
        navController.navigate(ShrineAppDestinations.AuthRoute.name) {
            popUpTo(ShrineAppDestinations.NavHostRoute.name) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    /**
     * Navigates to the password registration screen.
     */
    val navigateToRegister: () -> Unit = {
        navController.navigate(ShrineAppDestinations.RegisterPasswordRoute.name) {
            launchSingleTop = true
        }
    }
}
