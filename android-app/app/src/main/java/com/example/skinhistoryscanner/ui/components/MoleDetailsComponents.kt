package com.example.skinhistoryscanner.ui.components

import androidx.compose.ui.res.stringResource
import com.example.skinhistoryscanner.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.UserSettings
import com.example.skinhistoryscanner.ui.getBodyImageRes
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity

@Composable
fun MoleSummaryHeader(
    mole: Mole,
    variant: BackgroundVariantEntity?,
    userSettings: UserSettings,
    colorLabel: String,
    latestPhoto: HistoryEntry?,
    onColorClick: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onEditPhoto: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (latestPhoto?.imagePath != null) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(latestPhoto.imagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.latest_photo),
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onPhotoClick(latestPhoto.imagePath) },
                        contentScale = ContentScale.Crop
                    )
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = 300f))
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.latest_record), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(latestPhoto.date.toString(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = onEditPhoto,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.edit), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(android.graphics.Color.parseColor(mole.color)), CircleShape)
                            .clickable { onColorClick() }
                            .padding(4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.2f), CircleShape))
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column {
                        Text(colorLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                variant?.name ?: "Sconosciuto",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(100.dp, 140.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val zoomFactor = 2.5f
                val moleX = mole.x / 100f
                val moleY = mole.y / 100f
                
                // Disegna l'immagine in background zoomata
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    val imagePath = variant?.imagePath
                    if (imagePath == null && variant != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = getBodyImageRes(userSettings, variant.id)),
                            contentDescription = stringResource(R.string.desc_background_image),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomFactor
                                    scaleY = zoomFactor
                                    translationX = -(moleX - 0.5f) * size.width * zoomFactor
                                    translationY = -(moleY - 0.5f) * size.height * zoomFactor
                                },
                            contentScale = ContentScale.Crop
                        )
                    } else if (imagePath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(if (imagePath.startsWith("android.resource") || imagePath.startsWith("content://") || imagePath.startsWith("file://")) android.net.Uri.parse(imagePath) else File(imagePath))
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.desc_background_image),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomFactor
                                    scaleY = zoomFactor
                                    translationX = -(moleX - 0.5f) * size.width * zoomFactor
                                    translationY = -(moleY - 0.5f) * size.height * zoomFactor
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    // Disegna il neo
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(android.graphics.Color.parseColor(mole.color)),
                            radius = 6.dp.toPx(),
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: LocalDate) {
    val localDate = date
    val daysAgo = ChronoUnit.DAYS.between(localDate, LocalDate.now())
    val relativeText = when {
        daysAgo == 0L -> androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.today)
        daysAgo == 1L -> androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.yesterday)
        daysAgo < 7L -> androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.days_ago, daysAgo)
        daysAgo < 30L -> androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.weeks_ago, daysAgo / 7)
        else -> androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.months_ago, daysAgo / 30)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = localDate.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault())),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = relativeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun HistoryItem(
    entry: HistoryEntry, 
    onClick: () -> Unit, 
    onDelete: () -> Unit,
    onPhotoClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 20.dp, bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasImage = entry.imagePath != null
                    val hasNotes = !entry.notes.isNullOrBlank()
                    
                    val icon = when {
                        hasImage && hasNotes -> Icons.Default.AutoAwesome
                        hasImage -> Icons.Default.Image
                        else -> Icons.AutoMirrored.Filled.Notes
                    }
                    val tint = if (hasImage) MaterialTheme.colorScheme.primary else Color.Gray
                    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            hasImage && hasNotes -> androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.photo_and_note)
                            hasImage -> androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.photo)
                            else -> androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.note)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.delete), tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
            
            entry.imagePath?.let { imagePath ->
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(imagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.mole_photo),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPhotoClick(imagePath) },
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.example.skinhistoryscanner.R.string.edit), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            entry.notes?.let {
                if (it.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth().clickable { onClick() }
                    )
                }
            }
        }
    }
}
