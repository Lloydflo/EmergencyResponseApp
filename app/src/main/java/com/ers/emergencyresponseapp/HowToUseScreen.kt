package com.ers.emergencyresponseapp

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val GuidePrimary = Color(0xFF4C8A89)

/**
 * Image-based responder guide.
 *
 * Add either:
 * 1. One long image named `how_to_tutorial.webp`, or
 * 2. Multiple images named `how_to_01.webp`, `how_to_02.webp`, and so on.
 *
 * Put the files in `app/src/main/res/drawable-nodpi/`. The screen discovers
 * them at runtime, so no Kotlin changes are needed whenever a tutorial image
 * is replaced or another numbered page is added.
 */
private data class TutorialImage(
    val resourceId: Int,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tutorialImages = remember(context) { findTutorialImages(context) }
    var enlargedImage by remember { mutableStateOf<TutorialImage?>(null) }

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
            if (tutorialImages.isEmpty()) {
                item(key = "tutorial_empty") {
                    MissingTutorialImagesCard()
                }
            } else {
                item(key = "tutorial_hint") {
                    TutorialHintCard(pageCount = tutorialImages.size)
                }

                itemsIndexed(
                    items = tutorialImages,
                    key = { _, item -> item.resourceId }
                ) { index, item ->
                    TutorialImageCard(
                        image = item,
                        pageNumber = index + 1,
                        pageCount = tutorialImages.size,
                        onOpen = { enlargedImage = item }
                    )
                }
            }

            item(key = "tutorial_back") {
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

    enlargedImage?.let { image ->
        TutorialImageViewer(
            image = image,
            onDismiss = { enlargedImage = null }
        )
    }
}

@Composable
private fun TutorialHintCard(pageCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = GuidePrimary.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = GuidePrimary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = GuidePrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (pageCount == 1) "Responder tutorial" else "$pageCount tutorial pages",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Scroll to continue. Tap an image to open the full-screen viewer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun TutorialImageCard(
    image: TutorialImage,
    pageNumber: Int,
    pageCount: Int,
    onOpen: () -> Unit
) {
    val painter = painterResource(id = image.resourceId)
    val ratio = imageAspectRatio(painter)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = image.label,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$pageNumber / $pageCount",
                        color = GuidePrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Image(
                painter = painter,
                contentDescription = image.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = if (pageCount > 1) {
                            RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                        } else {
                            RoundedCornerShape(22.dp)
                        }
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun MissingTutorialImagesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(22.dp),
                color = GuidePrimary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ImageIcon,
                        contentDescription = null,
                        tint = GuidePrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Tutorial images are being prepared",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "The image guide will appear here when it is included in the next app build.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TutorialImageViewer(
    image: TutorialImage,
    onDismiss: () -> Unit
) {
    val painter = painterResource(id = image.resourceId)
    var scale by remember(image.resourceId) { mutableStateOf(1f) }
    var offset by remember(image.resourceId) { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        offset = if (newScale <= 1.01f) Offset.Zero else offset + panChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.96f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painter,
                    contentDescription = image.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 72.dp)
                        .transformable(state = transformState)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.58f)
                ) {
                    Text(
                        text = "Pinch to zoom • Drag to move",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .background(Color.Black.copy(alpha = 0.62f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close image",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun imageAspectRatio(painter: Painter): Float {
    val size = painter.intrinsicSize
    val width = size.width
    val height = size.height
    return if (
        width.isFinite() &&
        height.isFinite() &&
        width > 0f &&
        height > 0f
    ) {
        (width / height).coerceIn(0.08f, 4f)
    } else {
        0.72f
    }
}

@SuppressLint("DiscouragedApi")
private fun findTutorialImages(context: Context): List<TutorialImage> {
    val resources = context.resources
    val packageName = context.packageName

    // A single, vertically designed tutorial poster takes priority when present.
    val singleImageId = resources.getIdentifier(
        "how_to_tutorial",
        "drawable",
        packageName
    )
    if (singleImageId != 0) {
        return listOf(
            TutorialImage(
                resourceId = singleImageId,
                label = "Responder tutorial"
            )
        )
    }

    // Otherwise load numbered pages in order. Gaps are allowed.
    return (1..30).mapNotNull { page ->
        val paddedName = "how_to_${page.toString().padStart(2, '0')}"
        val shortName = "how_to_$page"
        val id = resources.getIdentifier(paddedName, "drawable", packageName)
            .takeIf { it != 0 }
            ?: resources.getIdentifier(shortName, "drawable", packageName)
                .takeIf { it != 0 }

        id?.let {
            TutorialImage(
                resourceId = it,
                label = "Step $page"
            )
        }
    }
}
