package com.comai.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.comai.R
import com.comai.data.models.ChatMessage
import com.comai.ui.theme.*
import com.comai.voice.VoiceInteractionManager
import com.comai.voice.VoiceState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    voiceManager: VoiceInteractionManager? = null,
    onNavigateToAudio: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMemory: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val voiceState by voiceManager?.state?.collectAsState() ?: remember { mutableStateOf(VoiceState.IDLE) }
    val partialText by voiceManager?.partialText?.collectAsState() ?: remember { mutableStateOf("") }

    // Auto-scroll on new message
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.combinedClickable(
                            onClick = { /* normal click */ },
                            onLongClick = { onNavigateToDashboard() } // Secret override dashboard
                        )
                    ) {
                        // Comai Robot Avatar
                        Image(
                            painter = painterResource(id = R.drawable.comai_avatar),
                            contentDescription = "Comai Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Comai",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Online",
                                    color = OnlineGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMemory) {
                        Text("🧠", fontSize = 18.sp)
                    }
                    IconButton(onClick = onNavigateToAudio) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Mode",
                            tint = Color(0xFF64B5F6)
                        )
                    }
                    IconButton(onClick = onNavigateToDashboard) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu / Overrides",
                            tint = Color(0xFF64B5F6)
                        )
                    }
                }
            )
        },
        containerColor = DarkBackground,
        bottomBar = {
            Column {
                AnimatedVisibility(visible = voiceState != VoiceState.IDLE) {
                    Surface(
                        color = when (voiceState) {
                            VoiceState.LISTENING -> OnlineGreen.copy(alpha = 0.15f)
                            VoiceState.PROCESSING -> WarmAmber.copy(alpha = 0.15f)
                            VoiceState.SPEAKING -> UserBubbleColor.copy(alpha = 0.15f)
                            else -> DarkSurfaceVariant
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (voiceState) {
                                    VoiceState.LISTENING -> "🎤 Listening: ${partialText.ifBlank { "Speak now..." }}"
                                    VoiceState.PROCESSING -> "⏳ Processing speech..."
                                    VoiceState.SPEAKING -> "🔊 Speaking... (Tap mic to stop)"
                                    VoiceState.ERROR -> "⚠️ Speech recognition error"
                                    else -> ""
                                },
                                color = when (voiceState) {
                                    VoiceState.LISTENING -> OnlineGreen
                                    VoiceState.PROCESSING -> WarmAmber
                                    VoiceState.SPEAKING -> ElectricTeal
                                    else -> Color.White
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    onMicClick = {
                        when (voiceState) {
                            VoiceState.LISTENING -> voiceManager?.stopListening()
                            VoiceState.SPEAKING -> voiceManager?.cancel()
                            else -> voiceManager?.startListening()
                        }
                    },
                    voiceState = voiceState
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(msg = msg)
                }

                if (isTyping) {
                    item {
                        TypingIndicatorBubble()
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.isFromUser
    val timeFormatted = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(if (isUser) UserBubbleColor else AIBubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = msg.content,
                color = TextOnBubble,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormatted,
                    color = if (isUser) Color.White.copy(alpha = 0.75f) else TextTimestamp,
                    fontSize = 11.sp
                )
                if (isUser) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "✓✓",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AIBubbleColor)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Comai is thinking...",
            color = TextSecondary,
            fontSize = 13.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    voiceState: VoiceState = VoiceState.IDLE
) {
    Surface(
        color = DarkBackground,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment Icon
            IconButton(
                onClick = { /* future attachments */ },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attachment",
                    tint = Color(0xFF2979FF)
                )
            }

            // Input Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(InputBarBackground)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (text.isEmpty()) {
                            Text(
                                text = if (voiceState == VoiceState.LISTENING) "Listening..." else "Message",
                                color = if (voiceState == VoiceState.LISTENING) OnlineGreen else TextSecondary,
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF2979FF)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.Outlined.Assignment,
                        contentDescription = "Notes",
                        tint = Color(0xFF2979FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Camera Icon
            IconButton(
                onClick = { /* camera */ },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Camera",
                    tint = Color(0xFF2979FF)
                )
            }

            // Mic or Send Icon
            if (text.isNotBlank()) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2979FF))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            } else {
                val micBg = when (voiceState) {
                    VoiceState.LISTENING -> OnlineGreen
                    VoiceState.SPEAKING -> UserBubbleColor
                    VoiceState.PROCESSING -> WarmAmber
                    else -> Color.Transparent
                }
                val micTint = when (voiceState) {
                    VoiceState.LISTENING, VoiceState.SPEAKING, VoiceState.PROCESSING -> Color.White
                    else -> Color(0xFF2979FF)
                }

                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(micBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (voiceState == VoiceState.LISTENING) "Stop listening" else "Start speaking",
                        tint = micTint
                    )
                }
            }
        }
    }
}
