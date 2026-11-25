package ai.cosmic

import androidx.compose.runtime.Composable
import ai.cosmic.ui.chat.ChatScreen
import ai.cosmic.ui.chat.ChatViewModel

@Composable
fun MainNavigation(viewModel: ChatViewModel? = null) {
    if (viewModel != null) {
        ChatScreen(viewModel = viewModel)
    } else {
        ChatScreen()
    }
}
