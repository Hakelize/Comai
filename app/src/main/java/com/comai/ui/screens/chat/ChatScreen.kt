package com.comai.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.comai.R
import com.comai.data.models.ChatMessage
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.navigation.Routes
import com.comai.ui.theme.*
import com.comai.voice.VoiceInteractionManager
import com.comai.voice.VoiceState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    voiceManager: VoiceInteractionManager? = null,
    onBack: (() -> Unit)? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToRoutine: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToAudio: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    // Lifecycle: Disable TTS and cancel any ongoing speech recognition while user is in the Chat tab
    DisposableEffect(voiceManager) {
        viewModel.setChatTabActive(true)
        voiceManager?.cancel()
        onDispose {
            viewModel.setChatTabActive(false)
        }
    }

    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val idleVoiceStateFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(VoiceState.IDLE) }
    val emptyPartialTextFlow = remember { kotlinx.coroutines.flow.MutableStateFlow("") }
    val voiceState by (voiceManager?.state ?: idleVoiceStateFlow).collectAsState()
    val partialText by (voiceManager?.partialText ?: emptyPartialTextFlow).collectAsState()

    // ── Media Attachment State ───────────────────────────────────────
    var attachedMediaUri by remember { mutableStateOf<String?>(null) }
    var attachedMediaType by remember { mutableStateOf<String?>(null) }
    var attachedMediaName by remember { mutableStateOf<String?>(null) }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            attachedMediaUri = tempCameraUri.toString()
            attachedMediaType = "image"
            attachedMediaName = "Camera Photo.jpg"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission required to capture photos", Toast.LENGTH_SHORT).show()
        }
    }

    val launchCamera = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedMediaUri = uri.toString()
            attachedMediaType = "image"
            attachedMediaName = getFileNameFromUri(context, uri) ?: "Image.jpg"
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedMediaUri = uri.toString()
            attachedMediaType = "document"
            attachedMediaName = getFileNameFromUri(context, uri) ?: "Document"
        }
    }

    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0

    // Auto-scroll on new message, typing indicator, or keyboard opening
    LaunchedEffect(messages.size, isTyping, isKeyboardOpen) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showAttachmentDialog) {
        AttachmentSelectionDialog(
            onDismiss = { showAttachmentDialog = false },
            onTakePhoto = { launchCamera() },
            onPickImage = { pickImageLauncher.launch("image/*") },
            onPickFile = { pickFileLauncher.launch("*/*") }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.combinedClickable(
                            onClick = { /* normal click */ },
                            onLongClick = { onNavigateToDashboard() } // Developer dashboard
                        )
                    ) {
                        // Comai Robot Avatar
                        Image(
                            painter = painterResource(id = R.drawable.comai_avatar),
                            contentDescription = "Comai Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "COMAI",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
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
                                    text = stringResource(R.string.ready),
                                    color = OnlineGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMemory) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = stringResource(R.string.chat_memory_tooltip),
                            tint = ElectricTeal
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .windowInsetsPadding(WindowInsets.ime)
                    .navigationBarsPadding()
            ) {
                // Voice Recognition Status Banner
                AnimatedVisibility(
                    visible = voiceState != VoiceState.IDLE,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = when (voiceState) {
                            VoiceState.LISTENING -> OnlineGreen.copy(alpha = 0.15f)
                            VoiceState.PROCESSING -> WarmAmber.copy(alpha = 0.15f)
                            VoiceState.SPEAKING -> ElectricTeal.copy(alpha = 0.15f)
                            VoiceState.ERROR -> ErrorRed.copy(alpha = 0.15f)
                            else -> Color.Transparent
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
                            Icon(
                                imageVector = when (voiceState) {
                                    VoiceState.LISTENING -> Icons.Default.Mic
                                    VoiceState.PROCESSING -> Icons.Outlined.Sync
                                    VoiceState.SPEAKING -> Icons.Outlined.GraphicEq
                                    VoiceState.ERROR -> Icons.Default.MicOff
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = when (voiceState) {
                                    VoiceState.LISTENING -> OnlineGreen
                                    VoiceState.PROCESSING -> WarmAmber
                                    VoiceState.SPEAKING -> ElectricTeal
                                    else -> Color.White
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val partialOrFallback = partialText.ifBlank { stringResource(R.string.speak_now) }
                            Text(
                                text = when (voiceState) {
                                    VoiceState.LISTENING -> stringResource(R.string.chat_voice_listening, partialOrFallback)
                                    VoiceState.PROCESSING -> stringResource(R.string.chat_voice_processing)
                                    VoiceState.SPEAKING -> stringResource(R.string.chat_voice_speaking)
                                    VoiceState.ERROR -> stringResource(R.string.chat_voice_error)
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

                // Media Attachment Preview Banner (if selected)
                if (attachedMediaUri != null) {
                    Surface(
                        color = Color(0xFF161E2E),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF273148)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (attachedMediaType == "image") Icons.Outlined.Image else Icons.Outlined.InsertDriveFile,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = attachedMediaName ?: stringResource(R.string.chat_photo_attached),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    attachedMediaUri = null
                                    attachedMediaType = null
                                    attachedMediaName = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    hasAttachment = attachedMediaUri != null,
                    onSend = {
                        if (inputText.isNotBlank() || attachedMediaUri != null) {
                            viewModel.sendMessage(
                                text = inputText,
                                mediaUri = attachedMediaUri,
                                mediaType = attachedMediaType,
                                mediaName = attachedMediaName,
                                speakResponse = false
                            )
                            inputText = ""
                            attachedMediaUri = null
                            attachedMediaType = null
                            attachedMediaName = null
                        }
                    },
                    onAttachClick = { showAttachmentDialog = true },
                    onCameraClick = { launchCamera() },
                    onMicClick = {
                        when (voiceState) {
                            VoiceState.LISTENING -> voiceManager?.stopListening()
                            VoiceState.SPEAKING -> voiceManager?.cancel()
                            else -> voiceManager?.startListening()
                        }
                    },
                    voiceState = voiceState
                )

                // ── Unified Tab Switching Navigation Bar ────────────────
                // Shows the identical navigation panel as Home/Profile when software keyboard is closed
                if (!isKeyboardOpen) {
                    ComaiBottomBar(
                        currentRoute = Routes.CHAT,
                        onNavigate = { targetRoute ->
                            when (targetRoute) {
                                Routes.HOME -> onNavigateToHome()
                                Routes.ROUTINE -> onNavigateToRoutine()
                                Routes.PROFILE -> onNavigateToProfile()
                                Routes.MEMORY -> onNavigateToMemory()
                            }
                        }
                    )
                }
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
            // Attached Media (Image or Document)
            if (msg.mediaUri != null) {
                MediaAttachmentBubble(
                    uriString = msg.mediaUri,
                    mediaType = msg.mediaType,
                    mediaName = msg.mediaName
                )
            }

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
fun MediaAttachmentBubble(
    uriString: String,
    mediaType: String?,
    mediaName: String?
) {
    val context = LocalContext.current
    var bitmap by remember(uriString) {
        mutableStateOf(loadLocalBitmap(context, uriString))
    }

    if (mediaType == "image" && bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = mediaName ?: "Attached Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
    } else {
        Surface(
            color = Color(0xFF161E2E),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (mediaType == "image") Icons.Outlined.Image else Icons.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = ElectricTeal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = mediaName ?: if (mediaType == "image") "Photo Attachment" else "Attached Document",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

private fun loadLocalBitmap(context: Context, uriString: String): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ElectricTeal)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ElectricTeal.copy(alpha = 0.6f))
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ElectricTeal.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    hasAttachment: Boolean = false,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit,
    voiceState: VoiceState
) {
    Surface(
        color = DarkBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [ + / Attachment ] Icon
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach media",
                    tint = if (hasAttachment) ElectricTeal else Color(0xFF2979FF)
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
                                text = if (voiceState == VoiceState.LISTENING) stringResource(R.string.listening) else stringResource(R.string.message_hint),
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
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Camera Icon
            IconButton(
                onClick = onCameraClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Camera",
                    tint = Color(0xFF2979FF)
                )
            }

            // Send or Mic Icon
            if (text.isNotBlank() || hasAttachment) {
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

@Composable
fun AttachmentSelectionDialog(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141926),
        title = {
            Text(stringResource(R.string.chat_attach_title), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = Color(0xFF1B2336),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onTakePhoto()
                        }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoCamera, null, tint = ElectricTeal, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_take_photo), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Surface(
                    color = Color(0xFF1B2336),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onPickImage()
                        }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Image, null, tint = Color(0xFF2979FF), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_select_image), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Surface(
                    color = Color(0xFF1B2336),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onPickFile()
                        }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.InsertDriveFile, null, tint = WarmAmber, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_select_file), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TextSecondary)
            }
        }
    )
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
