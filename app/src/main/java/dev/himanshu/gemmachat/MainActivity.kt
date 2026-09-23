package dev.himanshu.gemmachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.himanshu.gemmachat.ui.theme.GemmaChatTheme

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<ChatViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GemmaChatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainChatUi(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun MainChatUi(modifier: Modifier = Modifier, viewModel: ChatViewModel) {

    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isReady by viewModel.isReady.collectAsState()
    val status by viewModel.status.collectAsState()

    var input by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last == null || last.index >= messages.lastIndex
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty() && isAtBottom) {
            listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(12.dp)
    ) {

        Text(
            "Gemma Chat (Offline) ",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(8.dp)
        )


        AnimatedVisibility(
            isReady.not(),
            enter = fadeIn(tween(700)),
            exit = fadeOut(tween(700))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(status)
                }
            }
            return@AnimatedVisibility
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) {
                MessageBubble(
                    modifier = Modifier.animateItem(),
                    message = it,
                    // Only the last Gemma bubble, while a response is streaming.
                    isStreaming = isGenerating && !it.fromUser && it.id == messages.lastOrNull()?.id
                )
                if (messages.size - 1 == messages.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }

        AnimatedVisibility(
            isGenerating,
            enter = slideInVertically(tween(700)) { it } + fadeIn(tween(700)),
            exit = slideOutVertically(tween(700)) { it } + fadeOut(tween(700))
        ) {
            Text(
                "Gemma is generating response...",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }


        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask something...") },
                modifier = Modifier.weight(1f),
                enabled = !isGenerating,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (input.isNotBlank()) {
                        viewModel.send(input)
                        input = ""
                    }
                })
            )

            Spacer(Modifier.width(8.dp))

            Button(onClick = {
                if (input.isNotBlank()) {
                    viewModel.send(input)
                    input = ""
                }
            }, enabled = isGenerating.not() && input.isNotBlank()) {
                Text("Send")
            }

        }
    }
}

@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    message: ChatMessage,
    isStreaming: Boolean = false
) {

    val bubbleColor =
        if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else Color.Green.copy(alpha = 0.5f)


    val transitionState =
        remember { MutableTransitionState(initialState = false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = transitionState,
        modifier = modifier,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { fullHeight -> fullHeight / 2 }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .animateContentSize(),
                color = bubbleColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        if (message.fromUser) "You" else "Gemma",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (message.fromUser) {
                        Text(message.text.ifEmpty { "_" })
                    } else {
                        val mdState = rememberMarkdownState(
                            content = message.text.ifEmpty { "_" },
                            retainState = true
                        )
                        Markdown(mdState)
                    }
                }
            }
        }
    }
}

