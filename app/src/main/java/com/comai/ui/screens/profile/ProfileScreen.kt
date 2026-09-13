package com.comai.ui.screens.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.comai.R
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.components.ComaiCompactTimeBar
import com.comai.ui.navigation.Routes
import com.comai.ui.screens.home.HomeViewModel
import com.comai.ui.screens.onboarding.OnboardingPreferences
import com.comai.ui.screens.onboarding.UserOnboardingProfile
import com.comai.ui.theme.*
import com.comai.util.TimeUtils
import com.comai.voice.ComaiLanguage
import java.io.File

@Composable
fun ProfileScreen(
    homeViewModel: HomeViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToRoutine: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToPersonalSchedule: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToDigitalActivity: () -> Unit = {},
    onLanguageChanged: (ComaiLanguage) -> Unit = {}
) {
    val context = LocalContext.current
    val onboardingPreferences = remember { OnboardingPreferences(context) }
    val homeState by homeViewModel.uiState.collectAsState()
    val profile = homeState.profile

    // Dialog controls
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showEditRoutineDialog by remember { mutableStateOf(false) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }

    // Profile photo handling (stored locally in filesDir)
    val profilePhotoFile = remember { File(context.filesDir, "profile_photo.jpg") }
    var photoVersion by remember { mutableIntStateOf(0) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val profileBitmap: ImageBitmap? = remember(photoVersion) {
        if (profilePhotoFile.exists() && profilePhotoFile.length() > 0L) {
            try {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeFile(profilePhotoFile.absolutePath, opts)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val tempFile = File(context.cacheDir, "camera_profile_temp.jpg")
            if (tempFile.exists() && tempFile.length() > 0L) {
                tempFile.copyTo(profilePhotoFile, overwrite = true)
                photoVersion++
                Toast.makeText(context, context.getString(R.string.profile_photo_updated), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tempFile = File(context.cacheDir, "camera_profile_temp.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, context.getString(R.string.profile_cam_perm_needed), Toast.LENGTH_SHORT).show()
        }
    }

    val launchCamera = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val tempFile = File(context.cacheDir, "camera_profile_temp.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
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
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    profilePhotoFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                photoVersion++
                Toast.makeText(context, context.getString(R.string.profile_photo_updated), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Photo options dialog
    if (showPhotoOptionsDialog) {
        ProfilePhotoOptionsDialog(
            hasExistingPhoto = profileBitmap != null,
            onDismiss = { showPhotoOptionsDialog = false },
            onTakePhoto = {
                showPhotoOptionsDialog = false
                launchCamera()
            },
            onChooseGallery = {
                showPhotoOptionsDialog = false
                pickImageLauncher.launch("image/*")
            },
            onRemovePhoto = {
                showPhotoOptionsDialog = false
                if (profilePhotoFile.exists()) {
                    profilePhotoFile.delete()
                }
                photoVersion++
                Toast.makeText(context, context.getString(R.string.profile_photo_removed), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Unified Edit Profile dialog
    if (showEditProfileDialog) {
        EditProfileDialog(
            currentProfile = profile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { updatedProfile ->
                val prevLanguage = profile.preferredLanguage
                onboardingPreferences.saveProfile(updatedProfile)
                homeViewModel.refreshState()
                if (updatedProfile.preferredLanguage != prevLanguage) {
                    onLanguageChanged(updatedProfile.preferredLanguage)
                }
                showEditProfileDialog = false
                Toast.makeText(context, context.getString(R.string.profile_saved_success), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Routine editing dialog (keeps clock picker)
    if (showEditRoutineDialog) {
        EditDailyRoutineDialog(
            currentWakeTime = profile.wakeTime,
            currentLeaveTime = profile.leaveHomeTime,
            currentReturnTime = profile.returnHomeTime,
            currentSleepTime = profile.sleepTime,
            onDismiss = { showEditRoutineDialog = false },
            onSave = { wake, leave, ret, sleep ->
                val currentProf = onboardingPreferences.getProfile()
                onboardingPreferences.saveProfile(
                    currentProf.copy(
                        wakeTime = wake,
                        leaveHomeTime = leave,
                        returnHomeTime = ret,
                        sleepTime = sleep
                    )
                )
                homeViewModel.refreshState()
                showEditRoutineDialog = false
            }
        )
    }

    // Dedicated Settings dialog (opened via top-right ⚙ gear)
    if (showSettingsDialog) {
        SettingsDialog(
            profile = profile,
            onDismiss = { showSettingsDialog = false },
            onOpenLanguage = {
                showLanguageDialog = true
            },
            onNavigateToDigitalActivity = {
                showSettingsDialog = false
                onNavigateToDigitalActivity()
            },
            onNavigateToMemory = {
                showSettingsDialog = false
                onNavigateToMemory()
            }
        )
    }

    // Language selection dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = profile.preferredLanguage,
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { lang ->
                val currentProf = onboardingPreferences.getProfile()
                onboardingPreferences.saveProfile(currentProf.copy(preferredLanguage = lang))
                homeViewModel.refreshState()
                onLanguageChanged(lang)
                showLanguageDialog = false
            }
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                ComaiBottomBar(
                    currentRoute = Routes.PROFILE,
                    onNavigate = { targetRoute ->
                        when (targetRoute) {
                            Routes.HOME -> onNavigateToHome()
                            Routes.CHAT -> onNavigateToChat()
                            Routes.ROUTINE -> onNavigateToRoutine()
                            Routes.MEMORY -> onNavigateToMemory()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════
            // TOP BAR: SETTINGS GEAR IN TOP-RIGHT CORNER
            // ════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141A26))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = ElectricTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════
            // 1. PROFILE HEADER
            // PHOTO ON LEFT, INFORMATION ON RIGHT
            // ════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile photo on the LEFT
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricTeal.copy(alpha = 0.25f), SoftPurple.copy(alpha = 0.25f))
                                )
                            )
                            .clickable { showPhotoOptionsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap,
                                contentDescription = stringResource(R.string.profile_photo_options),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            val initialChar = profile.name.trim().firstOrNull()?.uppercaseChar() ?: 'C'
                            Text(
                                text = initialChar.toString(),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricTeal
                            )
                        }
                    }

                    // Small camera badge on bottom-right of photo
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color(0xFF161E2E))
                            .border(1.5.dp, Color(0xFF10141D), CircleShape)
                            .clickable { showPhotoOptionsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = stringResource(R.string.profile_photo_options),
                            tint = ElectricTeal,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                // User details on the RIGHT
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 1. Name
                    Text(
                        text = profile.name.ifBlank { "User" },
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // 2. Weekday / User Type
                    Text(
                        text = profile.weekdayType.ifBlank { "Member" },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 3. Workplace / College
                    val place = profile.placeName.ifBlank {
                        profile.workplace.ifBlank { profile.college.ifBlank { "Workplace / Campus not set" } }
                    }
                    Text(
                        text = place,
                        color = ElectricTeal,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 4. Gender
                    Text(
                        text = profile.gender.ifBlank { "Prefer not to say" },
                        color = TextSecondary.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ONE Main "Edit Profile" Button
            Button(
                onClick = { showEditProfileDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF14242A),
                    contentColor = ElectricTeal
                ),
                border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.profile_edit_profile),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ════════════════════════════════════════════════════════════
            // 2. DAILY ROUTINE (WITH SEPARATE COMPARTMENT INDICATORS)
            // ════════════════════════════════════════════════════════════
            HorizontalDivider(color = Color(0xFF1E2638), thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = stringResource(R.string.profile_daily_routine_section),
                    color = ElectricTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Non-scrollable, responsive 24-hour horizontal timeline (4 points: Wake, Leave, Return, Sleep)
            FitDailyRoutineTimeline(profile = profile)

            Spacer(modifier = Modifier.height(10.dp))

            // Thin horizontal line indicator representing separate compartment
            HorizontalDivider(color = Color(0xFF1E2638), thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // ONE "Edit Daily Routine" Button
            Button(
                onClick = { showEditRoutineDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF14242A),
                    contentColor = ElectricTeal
                ),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2638))
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.profile_edit_daily_routine),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════
// 24-HOUR ROUTINE TIMELINE (FIT COMPLETELY ON SCREEN, NO SCROLL)
// EXACTLY 4 POINTS: WAKE UP (ABOVE), LEAVE (BELOW), RETURN (ABOVE), SLEEP (BELOW)
// DISPLAYED AS HORIZONTAL / LANDSCAPE RECTANGLES (>= 2:1 RATIO)
// ════════════════════════════════════════════════════════════

data class RoutinePoint(
    val title: String,
    val timeLabel: String,
    val hourFraction: Float,
    val icon: ImageVector,
    val accentColor: Color,
    val isAbove: Boolean
)

@Composable
fun FitDailyRoutineTimeline(profile: UserOnboardingProfile) {
    val wakeHour = parseTimeToHourFraction(profile.wakeTime, 7.0f)
    val leaveHour = parseTimeToHourFraction(profile.leaveHomeTime, 8.5f)
    val returnHour = parseTimeToHourFraction(profile.returnHomeTime, 18.0f)
    val sleepHour = parseTimeToHourFraction(profile.sleepTime, 23.0f)

    // Exactly four points strictly alternating: Above -> Below -> Above -> Below
    val points = remember(profile) {
        listOf(
            RoutinePoint(
                title = "Wake Up",
                timeLabel = TimeUtils.normalizeTo12Hour(profile.wakeTime, "07:00 AM"),
                hourFraction = wakeHour,
                icon = Icons.Outlined.WbSunny,
                accentColor = ElectricTeal,
                isAbove = true
            ),
            RoutinePoint(
                title = "Leave",
                timeLabel = TimeUtils.normalizeTo12Hour(profile.leaveHomeTime, "08:30 AM"),
                hourFraction = leaveHour,
                icon = Icons.Outlined.DirectionsWalk,
                accentColor = Color(0xFF38BDF8),
                isAbove = false
            ),
            RoutinePoint(
                title = "Return",
                timeLabel = TimeUtils.normalizeTo12Hour(profile.returnHomeTime, "06:00 PM"),
                hourFraction = returnHour,
                icon = Icons.Outlined.Home,
                accentColor = SoftPurple,
                isAbove = true
            ),
            RoutinePoint(
                title = "Sleep",
                timeLabel = TimeUtils.normalizeTo12Hour(profile.sleepTime, "11:00 PM"),
                hourFraction = sleepHour,
                icon = Icons.Outlined.Nightlight,
                accentColor = Color(0xFFC084FC),
                isAbove = false
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
    ) {
        val totalWidth = maxWidth
        val cardWidth = 74.dp
        val cardHeight = 35.dp
        val timelineY = 50.dp
        val cardTopAbove = 4.dp
        val cardTopBelow = 65.dp

        // 1. Continuous 24-hour horizontal timeline bar across the complete width (at Y = 50.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .offset(y = timelineY - 1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1E283D),
                            ElectricTeal.copy(alpha = 0.5f),
                            SoftPurple.copy(alpha = 0.5f),
                            Color(0xFF1E283D)
                        )
                    ),
                    shape = RoundedCornerShape(1.dp)
                )
        )

        // 2. Hour reference tick marks & labels (00, 06, 12, 18, 24) along the timeline
        val ticks = listOf(0, 6, 12, 18, 24)
        ticks.forEach { hour ->
            val fraction = hour / 24f
            val tickOffset = totalWidth * fraction

            Column(
                modifier = Modifier
                    .offset(x = (tickOffset - 10.dp).coerceIn(0.dp, totalWidth - 20.dp), y = 51.dp)
                    .width(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(3.dp)
                        .background(Color(0xFF334155))
                )
                Text(
                    text = String.format("%02d", hour),
                    color = Color(0xFF64748B),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. Exact fractional X positions for the four timeline points
        val pointXs = points.map { pt ->
            val fraction = (pt.hourFraction / 24f).coerceIn(0.04f, 0.96f)
            totalWidth * fraction
        }

        // 4. Compute horizontal positions preventing collisions on the same half-plane
        // ABOVE side: Card 0 (Wake Up) & Card 2 (Return)
        val ideal0 = (pointXs[0] - cardWidth / 2).coerceIn(0.dp, totalWidth - cardWidth)
        val ideal2 = (pointXs[2] - cardWidth / 2).coerceIn(0.dp, totalWidth - cardWidth)
        val (card0X, card2X) = if (ideal2 < ideal0 + cardWidth + 4.dp) {
            val c0 = minOf(ideal0, (pointXs[2] - cardWidth - 4.dp).coerceAtLeast(0.dp)).coerceIn(0.dp, totalWidth - cardWidth)
            val c2 = maxOf(ideal2, c0 + cardWidth + 4.dp).coerceAtMost(totalWidth - cardWidth)
            c0 to c2
        } else {
            ideal0 to ideal2
        }

        // BELOW side: Card 1 (Leave) & Card 3 (Sleep)
        val ideal1 = (pointXs[1] - cardWidth / 2).coerceIn(0.dp, totalWidth - cardWidth)
        val ideal3 = (pointXs[3] - cardWidth / 2).coerceIn(0.dp, totalWidth - cardWidth)
        val (card1X, card3X) = if (ideal3 < ideal1 + cardWidth + 4.dp) {
            val c1 = minOf(ideal1, (pointXs[3] - cardWidth - 4.dp).coerceAtLeast(0.dp)).coerceIn(0.dp, totalWidth - cardWidth)
            val c3 = maxOf(ideal3, c1 + cardWidth + 4.dp).coerceAtMost(totalWidth - cardWidth)
            c1 to c3
        } else {
            ideal1 to ideal3
        }

        val cardPlacements = listOf(
            card0X to cardTopAbove,
            card1X to cardTopBelow,
            card2X to cardTopAbove,
            card3X to cardTopBelow
        )

        // 5. Draw short vertical connector lines from cards to timeline dots
        points.forEachIndexed { i, pt ->
            val pX = pointXs[i]
            val (cX, cY) = cardPlacements[i]
            val connectorX = pX.coerceIn(cX + 8.dp, cX + cardWidth - 8.dp)

            if (pt.isAbove) {
                // Card is above -> connector runs from bottom of card to timeline line
                val startY = cY + cardHeight
                val endY = timelineY - 1.dp
                Box(
                    modifier = Modifier
                        .offset(x = connectorX - 0.5.dp, y = startY)
                        .width(1.dp)
                        .height((endY - startY).coerceAtLeast(1.dp))
                        .background(pt.accentColor.copy(alpha = 0.5f))
                )
            } else {
                // Card is below -> connector runs from timeline line to top of card
                val startY = timelineY + 3.dp
                val endY = cY
                Box(
                    modifier = Modifier
                        .offset(x = connectorX - 0.5.dp, y = startY)
                        .width(1.dp)
                        .height((endY - startY).coerceAtLeast(1.dp))
                        .background(pt.accentColor.copy(alpha = 0.5f))
                )
            }
        }

        // 6. Draw 4 circular timeline dots exactly on the main 24-hour line
        points.forEachIndexed { i, pt ->
            Box(
                modifier = Modifier
                    .offset(x = pointXs[i] - 3.5.dp, y = timelineY - 3.5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(pt.accentColor)
                    .border(1.2.dp, Color(0xFF11151F), CircleShape)
            )
        }

        // 7. Render the 4 compact horizontal/landscape event cards
        points.forEachIndexed { i, pt ->
            val (cX, cY) = cardPlacements[i]
            HorizontalRoutineCard(
                pt = pt,
                modifier = Modifier
                    .offset(x = cX, y = cY)
                    .width(cardWidth)
                    .height(cardHeight)
            )
        }
    }
}

@Composable
private fun HorizontalRoutineCard(
    pt: RoutinePoint,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF111722),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, pt.accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = pt.icon,
                    contentDescription = null,
                    tint = pt.accentColor,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = pt.title,
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = pt.timeLabel,
                color = pt.accentColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 15.dp)
            )
        }
    }
}

fun parseTimeToHourFraction(timeStr: String, defaultHour: Float): Float {
    if (timeStr.isBlank()) return defaultHour
    try {
        val trimmed = timeStr.trim().uppercase()
        val isPm = trimmed.contains("PM")
        val isAm = trimmed.contains("AM")
        val cleaned = trimmed.replace("AM", "").replace("PM", "").trim()
        val parts = cleaned.split(":")
        if (parts.isNotEmpty()) {
            var h = parts[0].trim().toIntOrNull() ?: return defaultHour
            val m = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 0 else 0
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            return (h + m / 60f).coerceIn(0f, 24f)
        }
    } catch (_: Exception) {
    }
    return defaultHour
}

// ════════════════════════════════════════════════════════════
// DEDICATED SETTINGS DIALOG (OPENED VIA TOP-RIGHT ⚙ GEAR)
// ════════════════════════════════════════════════════════════

@Composable
fun SettingsDialog(
    profile: UserOnboardingProfile,
    onDismiss: () -> Unit,
    onOpenLanguage: () -> Unit,
    onNavigateToDigitalActivity: () -> Unit,
    onNavigateToMemory: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF11151F),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2638)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                // Header with Title & Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── LANGUAGE ──────────────────────────────────────────
                Text(
                    text = stringResource(R.string.profile_language).uppercase(),
                    color = ElectricTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF181F2C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLanguage() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Translate, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = stringResource(R.string.profile_language), color = TextPrimary, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = profile.preferredLanguage.displayName, color = ElectricTeal, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── VOICE PREFERENCES ─────────────────────────────────
                Text(
                    text = stringResource(R.string.settings_voice_section).uppercase(),
                    color = ElectricTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF181F2C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = SoftPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Voice Assistance", color = TextPrimary, fontSize = 13.sp)
                        }
                        Text(text = "On-Device", color = SoftPurple, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── SYSTEM PERMISSIONS ────────────────────────────────
                Text(
                    text = stringResource(R.string.profile_permissions_section).uppercase(),
                    color = ElectricTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF181F2C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasActivity = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

                        SettingsPermissionRow(label = stringResource(R.string.profile_perm_mic), isGranted = hasMic, icon = Icons.Outlined.Mic)
                        HorizontalDivider(color = Color(0xFF1E2638), modifier = Modifier.padding(vertical = 8.dp))
                        SettingsPermissionRow(label = stringResource(R.string.profile_perm_location), isGranted = hasLocation, icon = Icons.Outlined.LocationOn)
                        HorizontalDivider(color = Color(0xFF1E2638), modifier = Modifier.padding(vertical = 8.dp))
                        SettingsPermissionRow(label = stringResource(R.string.profile_perm_activity), isGranted = hasActivity, icon = Icons.Outlined.DirectionsRun)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── SHORTCUTS & PRIVACY ───────────────────────────────
                Text(
                    text = "ACTIVITY & PRIVACY",
                    color = ElectricTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF181F2C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDigitalActivity() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BarChart, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = stringResource(R.string.profile_digital_activity), color = TextPrimary, fontSize = 13.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color(0xFF181F2C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToMemory() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Psychology, contentDescription = null, tint = SoftPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = stringResource(R.string.section_memories), color = TextPrimary, fontSize = 13.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Privacy card
                Surface(
                    color = Color(0xFF0F151B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1A262E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.settings_privacy_badge),
                            color = Color(0xFFB0C4DE),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricTeal,
                        contentColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsPermissionRow(
    label: String,
    isGranted: Boolean,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) ElectricTeal else Color(0xFF64748B),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 13.sp
            )
        }
        Surface(
            color = if (isGranted) Color(0xFF14242A) else Color(0xFF1E2129),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if (isGranted) ElectricTeal.copy(alpha = 0.4f) else Color(0xFF2A344A))
        ) {
            Text(
                text = stringResource(if (isGranted) R.string.profile_perm_granted else R.string.profile_perm_missing),
                color = if (isGranted) ElectricTeal else Color(0xFF8B9CB8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// PROFILE PHOTO OPTIONS DIALOG
// ════════════════════════════════════════════════════════════

@Composable
fun ProfilePhotoOptionsDialog(
    hasExistingPhoto: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF131722),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF222B3D)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.profile_photo_options),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                PhotoOptionItem(
                    icon = Icons.Outlined.PhotoCamera,
                    title = stringResource(R.string.profile_take_photo),
                    onClick = onTakePhoto
                )

                Spacer(modifier = Modifier.height(10.dp))

                PhotoOptionItem(
                    icon = Icons.Outlined.Image,
                    title = stringResource(R.string.profile_choose_gallery),
                    onClick = onChooseGallery
                )

                if (hasExistingPhoto) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PhotoOptionItem(
                        icon = Icons.Outlined.Delete,
                        title = stringResource(R.string.profile_remove_photo),
                        textColor = Color(0xFFFF5252),
                        iconTint = Color(0xFFFF5252),
                        onClick = onRemovePhoto
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoOptionItem(
    icon: ImageVector,
    title: String,
    textColor: Color = TextPrimary,
    iconTint: Color = ElectricTeal,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF181F2C),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// UNIFIED EDIT PROFILE DIALOG
// ════════════════════════════════════════════════════════════

@Composable
fun EditProfileDialog(
    currentProfile: UserOnboardingProfile,
    onDismiss: () -> Unit,
    onSave: (UserOnboardingProfile) -> Unit
) {
    var name by remember { mutableStateOf(currentProfile.name) }
    var selectedGender by remember { mutableStateOf(currentProfile.gender) }
    var selectedWeekdayType by remember { mutableStateOf(currentProfile.weekdayType.ifBlank { "Work" }) }
    var workplaceOrCollege by remember {
        mutableStateOf(currentProfile.placeName.ifBlank { currentProfile.workplace.ifBlank { currentProfile.college } })
    }
    var isNameError by remember { mutableStateOf(false) }

    val genderOptions = listOf(
        "Male" to R.string.gender_male,
        "Female" to R.string.gender_female,
        "Prefer not to say" to R.string.gender_prefer_not
    )

    val weekdayOptions = listOf(
        "Work" to R.string.weekday_work,
        "College" to R.string.weekday_college,
        "Both" to R.string.weekday_both,
        "Other" to R.string.weekday_other
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF131722),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF222B3D)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.profile_edit_profile_title),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Full Name
                Text(
                    text = stringResource(R.string.profile_field_name),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) isNameError = false
                    },
                    placeholder = { Text(stringResource(R.string.profile_enter_name)) },
                    singleLine = true,
                    isError = isNameError,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = Color(0xFF222B3D),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isNameError) {
                    Text(
                        text = "Name cannot be empty",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Weekday Type
                Text(
                    text = stringResource(R.string.profile_field_type),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    weekdayOptions.forEach { (type, resId) ->
                        val isSelected = selectedWeekdayType.equals(type, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedWeekdayType = type },
                            label = { Text(stringResource(resId), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF14242A),
                                selectedLabelColor = ElectricTeal,
                                containerColor = Color(0xFF181F2C),
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) ElectricTeal else Color(0xFF222B3D),
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Workplace / College
                Text(
                    text = stringResource(R.string.profile_field_place),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = workplaceOrCollege,
                    onValueChange = { workplaceOrCollege = it },
                    placeholder = { Text("e.g. Zoho Corp, PSG Tech") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = Color(0xFF222B3D),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Gender
                Text(
                    text = stringResource(R.string.profile_gender),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genderOptions.forEach { (gender, resId) ->
                        val isSelected = selectedGender.equals(gender, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedGender = gender },
                            label = { Text(stringResource(resId), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF14242A),
                                selectedLabelColor = ElectricTeal,
                                containerColor = Color(0xFF181F2C),
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) ElectricTeal else Color(0xFF222B3D),
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = TextSecondary, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            if (trimmedName.isBlank()) {
                                isNameError = true
                                return@Button
                            }
                            val updated = currentProfile.copy(
                                name = trimmedName,
                                gender = selectedGender,
                                weekdayType = selectedWeekdayType,
                                placeName = workplaceOrCollege.trim(),
                                workplace = if (selectedWeekdayType.equals("Work", true)) workplaceOrCollege.trim() else currentProfile.workplace,
                                college = if (selectedWeekdayType.equals("College", true)) workplaceOrCollege.trim() else currentProfile.college
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricTeal,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.profile_save_changes), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// DAILY ROUTINE EDIT DIALOG (PRESERVING CLOCK PICKER)
// ════════════════════════════════════════════════════════════

@Composable
fun EditDailyRoutineDialog(
    currentWakeTime: String,
    currentLeaveTime: String,
    currentReturnTime: String,
    currentSleepTime: String,
    onDismiss: () -> Unit,
    onSave: (wake: String, leave: String, ret: String, sleep: String) -> Unit
) {
    var wakeTime by remember { mutableStateOf(currentWakeTime) }
    var leaveTime by remember { mutableStateOf(currentLeaveTime) }
    var returnTime by remember { mutableStateOf(currentReturnTime) }
    var sleepTime by remember { mutableStateOf(currentSleepTime) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF131722),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF222B3D)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.profile_edit_daily_routine),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.profile_edit_routine_desc),
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Wake-up Time
                Text(
                    text = stringResource(R.string.profile_wake_time),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                ComaiCompactTimeBar(
                    value = wakeTime,
                    onValueChange = { wakeTime = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Leave Home
                Text(
                    text = stringResource(R.string.profile_leave_home),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                ComaiCompactTimeBar(
                    value = leaveTime,
                    onValueChange = { leaveTime = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Return Home
                Text(
                    text = stringResource(R.string.profile_return_home),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                ComaiCompactTimeBar(
                    value = returnTime,
                    onValueChange = { returnTime = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sleep Time
                Text(
                    text = stringResource(R.string.profile_sleep_time),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                ComaiCompactTimeBar(
                    value = sleepTime,
                    onValueChange = { sleepTime = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = TextSecondary, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onSave(
                                wakeTime.trim(),
                                leaveTime.trim(),
                                returnTime.trim(),
                                sleepTime.trim()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricTeal,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
