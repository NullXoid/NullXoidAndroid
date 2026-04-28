package com.nullxoid.android.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nullxoid.android.ui.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    state: AppUiState,
    onLogin: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onPasskeySetup: () -> Unit,
    onOidcSetup: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NullXoid") },
                actions = {
                    TextButton(
                        modifier = Modifier.testTag("login-backend-button"),
                        onClick = onOpenSettings
                    ) { Text("Backend") }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sign in", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                state.backendUrl.ifEmpty { "(no backend configured)" },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))
            Button(
                modifier = Modifier.testTag("login-passkey"),
                onClick = onPasskeySetup,
                enabled = !state.loading
            ) { Text("Sign in with passkey") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.testTag("login-oidc"),
                onClick = onOidcSetup,
                enabled = !state.loading
            ) { Text("Continue with OIDC") }
            Spacer(Modifier.height(16.dp))
            Text(
                "Password fallback is for development or migration only.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.testTag("login-username")
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.testTag("login-password")
            )
            Spacer(Modifier.height(24.dp))
            if (state.loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    modifier = Modifier.testTag("login-submit"),
                    onClick = { onLogin(username, password) },
                    enabled = username.isNotBlank() && password.isNotBlank()
                ) { Text("Sign in") }
            }
            state.error?.let { err ->
                Spacer(Modifier.height(16.dp))
                Text(err, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }
        }
    }
}
