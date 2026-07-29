package com.ers.emergencyresponseapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GuidePrimary = Color(0xFF4C8A89)
private val GuideSecondary = Color(0xFF3A506B)
private val GuideDark = Color(0xFF0B132B)
private val GuideDanger = Color(0xFFD94C4C)
private val GuideSuccess = Color(0xFF2E7D32)

private enum class GuidePreviewType {
    WELCOME,
    LOGIN,
    ASSIGNMENT,
    NAVIGATION,
    HOME_BACKUP,
    COMPLETION
}

private data class GuideStep(
    val number: Int,
    val title: String,
    val description: String,
    val primaryCallout: String,
    val secondaryCallout: String,
    val previewType: GuidePreviewType
)

private val guideSteps = listOf(
    GuideStep(
        number = 1,
        title = "Start the responder app",
        description = "Tap Proceed on the welcome screen. The How to use this app button opens this guide again whenever you need it.",
        primaryCallout = "Tap Proceed",
        secondaryCallout = "Open this guide",
        previewType = GuidePreviewType.WELCOME
    ),
    GuideStep(
        number = 2,
        title = "Sign in and verify",
        description = "Enter your registered email, tap Send OTP, enter the six-digit code, then tap Verify.",
        primaryCallout = "Enter email",
        secondaryCallout = "Verify OTP",
        previewType = GuidePreviewType.LOGIN
    ),
    GuideStep(
        number = 3,
        title = "Watch for your assignment",
        description = "On Home, a new card appears under Assigned Incidents. Review the type, priority, location, and incident details.",
        primaryCallout = "New incident card",
        secondaryCallout = "Review details",
        previewType = GuidePreviewType.ASSIGNMENT
    ),
    GuideStep(
        number = 4,
        title = "Navigate to the incident",
        description = "Tap Navigate to Incident and allow location access. The live route opens and your response status moves to En Route.",
        primaryCallout = "Start navigation",
        secondaryCallout = "Follow live route",
        previewType = GuidePreviewType.NAVIGATION
    ),
    GuideStep(
        number = 5,
        title = "Coordinate and request backup",
        description = "Open Coordination from the bottom navigation for responder messages. To request another unit or resource, return to Home and tap Request Backup in the Backup Requests card.",
        primaryCallout = "Backup Requests on Home",
        secondaryCallout = "Tap Request Backup",
        previewType = GuidePreviewType.HOME_BACKUP
    ),
    GuideStep(
        number = 6,
        title = "Complete and document",
        description = "Tap Complete Incident, add the required notes or proof, and submit. Post-incident records are available in Reviews.",
        primaryCallout = "Add proof and notes",
        secondaryCallout = "Complete incident",
        previewType = GuidePreviewType.COMPLETION
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "How to use this app",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "guide_intro") {
                GuideIntroCard()
            }

            items(guideSteps, key = { it.number }) { step ->
                GuideStepCard(step)
            }

            item(key = "guide_footer") {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GuidePrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Back to the app", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GuideIntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(GuidePrimary, GuideSecondary, GuideDark)
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "RESPONDER QUICK GUIDE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                Text(
                    text = "From sign-in to incident completion",
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Follow the six screens below. Important controls are labeled in each phone preview.",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun GuideStepCard(step: GuideStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(GuidePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = step.number.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = step.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            GuidePhonePreview(
                previewType = step.previewType,
                primaryCallout = step.primaryCallout,
                secondaryCallout = step.secondaryCallout
            )
        }
    }
}

@Composable
private fun GuidePhonePreview(
    previewType: GuidePreviewType,
    primaryCallout: String,
    secondaryCallout: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.96f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        GuidePrimary.copy(alpha = 0.13f),
                        MaterialTheme.colorScheme.surfaceVariant,
                        GuideSecondary.copy(alpha = 0.10f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(154.dp)
                .height(240.dp)
                .shadow(10.dp, RoundedCornerShape(28.dp), clip = false),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF111318)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF7F9F9))
            ) {
                when (previewType) {
                    GuidePreviewType.WELCOME -> MiniWelcomeScreen()
                    GuidePreviewType.LOGIN -> MiniLoginScreen()
                    GuidePreviewType.ASSIGNMENT -> MiniAssignmentScreen()
                    GuidePreviewType.NAVIGATION -> MiniNavigationScreen()
                    GuidePreviewType.HOME_BACKUP -> MiniHomeBackupScreen()
                    GuidePreviewType.COMPLETION -> MiniCompletionScreen()
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .width(48.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF111318))
                )
            }
        }

        GuideCallout(
            text = primaryCallout,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp)
        )
        GuideCallout(
            text = secondaryCallout,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp),
            emphasize = true
        )
    }
}

@Composable
private fun GuideCallout(
    text: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (emphasize) GuidePrimary else MaterialTheme.colorScheme.surface,
        border = if (emphasize) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        ),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (emphasize) Color.White else GuidePrimary)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = if (emphasize) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniStatusBar(title: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(GuidePrimary)
            .padding(start = 9.dp, end = 7.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniWelcomeScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .background(
                    Brush.linearGradient(
                        listOf(GuideDark, GuideSecondary, GuidePrimary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(90.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.43f
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = size.minDimension * 0.31f,
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = GuidePrimary,
                    radius = size.minDimension * 0.20f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.50f, size.height * 0.35f),
                    end = Offset(size.width * 0.50f, size.height * 0.65f),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.35f, size.height * 0.50f),
                    end = Offset(size.width * 0.65f, size.height * 0.50f),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Emergency Response",
                color = GuideDark,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            MiniTextLine(widthFraction = 0.78f)
            MiniTextLine(widthFraction = 0.62f)
            Spacer(Modifier.weight(1f))
            MiniButton("PROCEED", highlighted = true)
            Spacer(Modifier.height(5.dp))
            MiniButton("HOW TO USE THIS APP", highlighted = false)
        }
    }
}

@Composable
private fun MiniLoginScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniStatusBar(title = "Responder sign in", icon = Icons.Default.Security)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = GuidePrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Email Login",
                color = GuideDark,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            MiniField(label = "Registered email")
            Spacer(Modifier.height(8.dp))
            MiniButton("SEND OTP", highlighted = true)
            Spacer(Modifier.height(14.dp))
            Text(
                "OTP Verification",
                color = GuideDark,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(6) {
                    Box(
                        modifier = Modifier
                            .size(width = 17.dp, height = 22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .border(1.dp, GuidePrimary.copy(alpha = 0.55f), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("•", color = GuideDark, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MiniButton("VERIFY", highlighted = true)
        }
    }
}

@Composable
private fun MiniAssignmentScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .background(
                    Brush.linearGradient(listOf(GuidePrimary, GuideSecondary, GuideDark))
                )
                .padding(start = 10.dp, end = 8.dp, top = 15.dp, bottom = 7.dp)
        ) {
            Column {
                Text("Hello, Responder", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("Unit 12 • Available", color = Color.White.copy(alpha = 0.80f), fontSize = 5.sp)
            }
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Assigned Incidents",
                    color = GuideDark,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = GuideDanger.copy(alpha = 0.12f), shape = CircleShape) {
                    Text(
                        "1",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = GuideDanger,
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, GuideDanger.copy(alpha = 0.38f))
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(GuideDanger.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = GuideDanger,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fire Incident", color = GuideDark, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            Text("HIGH PRIORITY", color = GuideDanger, fontSize = 5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = GuidePrimary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("Barangay response location", color = Color(0xFF575757), fontSize = 5.5.sp)
                    }
                    MiniTextLine(widthFraction = 0.95f)
                    MiniTextLine(widthFraction = 0.72f)
                    MiniButton("NAVIGATE TO INCIDENT", highlighted = true)
                }
            }
            Spacer(Modifier.weight(1f))
            MiniBottomNavigation(selected = 0)
        }
    }
}

@Composable
private fun MiniNavigationScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE8EFEE))
        ) {
            val gridColor = Color(0xFFCBD8D6)
            val step = size.width / 6f
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1.2f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.2f)
                y += step
            }

            val route = Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.78f)
                cubicTo(
                    size.width * 0.24f,
                    size.height * 0.46f,
                    size.width * 0.64f,
                    size.height * 0.70f,
                    size.width * 0.82f,
                    size.height * 0.27f
                )
            }
            drawPath(route, color = Color.White, style = Stroke(width = 11f, cap = StrokeCap.Round))
            drawPath(route, color = GuidePrimary, style = Stroke(width = 6f, cap = StrokeCap.Round))
            drawCircle(GuidePrimary, 12f, Offset(size.width * 0.12f, size.height * 0.78f))
            drawCircle(GuideDanger, 14f, Offset(size.width * 0.82f, size.height * 0.27f))
            drawCircle(Color.White, 5f, Offset(size.width * 0.82f, size.height * 0.27f))
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 29.dp, start = 8.dp, end = 8.dp)
                .fillMaxWidth(),
            color = Color.White.copy(alpha = 0.96f),
            shape = RoundedCornerShape(9.dp),
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    tint = GuidePrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("En Route", color = GuideDark, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text("4.2 km • 9 min", color = Color(0xFF575757), fontSize = 5.sp)
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
                .fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 5.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Incident destination", color = GuideDark, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                MiniTextLine(widthFraction = 0.78f)
                Spacer(Modifier.height(6.dp))
                MiniButton("CONTINUE ROUTE", highlighted = true)
            }
        }
    }
}

@Composable
private fun MiniHomeBackupScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    Brush.linearGradient(listOf(GuidePrimary, GuideSecondary, GuideDark))
                )
                .padding(start = 10.dp, end = 8.dp, top = 15.dp, bottom = 7.dp)
        ) {
            Column {
                Text(
                    "Home",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Unit 12 • Available",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 5.sp
                )
            }
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                "Assigned Incidents",
                color = GuideDark,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, GuideDanger.copy(alpha = 0.28f))
            ) {
                Row(
                    modifier = Modifier.padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(GuideDanger.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = GuideDanger,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Active incident",
                            color = GuideDark,
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Response in progress",
                            color = Color(0xFF575757),
                            fontSize = 5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(7.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, GuidePrimary.copy(alpha = 0.38f))
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(GuidePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalHospital,
                                contentDescription = null,
                                tint = GuidePrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Backup Requests",
                                color = GuideDark,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Additional units or resources",
                                color = Color(0xFF575757),
                                fontSize = 4.8.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        MiniCompactButton(
                            label = "REQUEST BACKUP",
                            highlighted = true,
                            modifier = Modifier.weight(1.35f)
                        )
                        MiniCompactButton(
                            label = "VIEW ALL",
                            highlighted = false,
                            modifier = Modifier.weight(0.75f)
                        )
                    }

                    Text(
                        "No backup requests yet.",
                        color = Color(0xFF6F7978),
                        fontSize = 5.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            MiniBottomNavigation(selected = 0)
        }
    }
}

@Composable
private fun MiniCompactButton(
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (highlighted) GuidePrimary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (highlighted) GuidePrimary else GuidePrimary.copy(alpha = 0.65f),
                shape = RoundedCornerShape(7.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (highlighted) Color.White else GuidePrimary,
            fontSize = 4.8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun MiniCompletionScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniStatusBar(title = "Complete incident", icon = Icons.Default.Done)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text("Completion proof", color = GuideDark, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(GuidePrimary.copy(alpha = 0.08f))
                    .border(1.dp, GuidePrimary.copy(alpha = 0.35f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = GuidePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Add photo", color = GuidePrimary, fontSize = 5.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            MiniField(label = "Completion notes", height = 50.dp)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = GuideSuccess.copy(alpha = 0.10f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = null,
                        tint = GuideSuccess,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Ready to submit",
                        color = GuideSuccess,
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            MiniButton("COMPLETE INCIDENT", highlighted = true)
        }
    }
}

@Composable
private fun MiniField(label: String, height: androidx.compose.ui.unit.Dp = 34.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFD6DEDD), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF7A8585),
            fontSize = 5.5.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MiniButton(label: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(25.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) GuidePrimary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (highlighted) GuidePrimary else GuidePrimary.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (highlighted) Color.White else GuidePrimary,
            fontSize = 5.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun MiniTextLine(widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFD9E1E0))
    )
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun MiniBottomNavigation(selected: Int) {
    HorizontalDivider(color = Color(0xFFE1E6E5))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        listOf(Icons.Default.Home, Icons.Default.Groups, Icons.Default.Done).forEachIndexed { index, icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (index == selected) GuidePrimary else Color(0xFF9AA4A3),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
