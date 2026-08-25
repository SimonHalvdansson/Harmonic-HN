package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.presentation.UserMessageStore
import kotlinx.coroutines.flow.collect

/** Renders the shared application message queue using the host's Material theme. */
@Composable
fun UserMessageSnackbarHost(
    messages: UserMessageStore,
    modifier: Modifier = Modifier,
) {
    val hostState = remember { SnackbarHostState() }

    LaunchedEffect(messages) {
        messages.messages.collect { message ->
            hostState.showSnackbar(
                message = message.text,
                duration = when (message.duration) {
                    UserMessageDuration.SHORT -> SnackbarDuration.Short
                    UserMessageDuration.LONG -> SnackbarDuration.Long
                },
            )
        }
    }

    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
