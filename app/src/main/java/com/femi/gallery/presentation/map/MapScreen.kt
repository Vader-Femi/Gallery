package com.femi.gallery.presentation.map

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.femi.gallery.R
import com.femi.gallery.presentation.common.gridSharedBounds
import com.femi.gallery.domain.geo.GeoBox
import com.femi.gallery.domain.geo.GeoCluster
import com.femi.gallery.domain.geo.LabelPlacer
import com.femi.gallery.domain.geo.LocatedMedia
import com.femi.gallery.domain.geo.LocationScanState
import com.femi.gallery.domain.geo.LocationScanStatus
import com.femi.gallery.domain.geo.MapClusterer
import com.femi.gallery.domain.geo.MapData
import com.femi.gallery.domain.geo.MapPolyline
import com.femi.gallery.domain.geo.MapViewport
import com.femi.gallery.domain.geo.Mercator
import com.femi.gallery.domain.geo.PlaceVisibility
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

/** The deepest the map zooms: about 2.4 metres per pixel at the equator. */
private const val MaxZoom = 17.0

/** Photos closer together than this (in dp) are grouped into one bubble. */
private const val ClusterCellDp = 76f
private val BubbleSize = 56.dp

/**
 * The map: land, borders and cities drawn from data that ships with the app (no tiles, no network),
 * with the user's geotagged photos grouped into bubbles that open those photos when tapped.
 */
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onOpenCluster: (GeoCluster) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val mapData by viewModel.mapData.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val viewport by viewModel.viewport.collectAsStateWithLifecycle()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val colors = mapColors()

    // New photos get their place read whenever the map comes back to the front.
    LifecycleResumeEffect(Unit) {
        viewModel.scanLocations()
        onPauseOrDispose { }
    }

    // Start by showing every photo (or the whole world while there are none).
    LaunchedEffect(size, media.size, loaded, scan.status) {
        val ready = media.size > 0 || (loaded && scan.status != LocationScanStatus.Scanning)
        if (viewport == null && size.width > 0 && size.height > 0 && ready) {
            viewModel.setViewport(showAll(media, size.width.toDouble(), size.height.toDouble()))
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.ocean)
            .onSizeChanged { size = it },
    ) {
        viewport?.let { current ->
            MapCanvas(
                data = mapData,
                media = media,
                viewport = current,
                colors = colors,
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                onTransform = viewModel::updateViewport,
                onClusterClick = onOpenCluster,
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.viewer_back))
                }
                Text(
                    text = stringResource(R.string.map_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (size.width > 0) {
                            viewModel.setViewport(showAll(media, size.width.toDouble(), size.height.toDouble()))
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Public, contentDescription = stringResource(R.string.map_show_all))
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapStatus(
                scan = scan,
                showEmpty = loaded && media.size == 0,
                onPermissionResult = viewModel::scanLocations,
            )
            Text(
                text = stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

private fun showAll(media: LocatedMedia, width: Double, height: Double): MapViewport {
    val box = media.boundingBox() ?: GeoBox(south = -55.0, west = -170.0, north = 72.0, east = 170.0)
    return MapViewport.fit(box, width, height, paddingPixels = 120.0, maxZoom = 12.0)
}

@Composable
private fun MapCanvas(
    data: MapData,
    media: LocatedMedia,
    viewport: MapViewport,
    colors: MapColors,
    width: Float,
    height: Float,
    onTransform: ((MapViewport) -> MapViewport) -> Unit,
    onClusterClick: (GeoCluster) -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        color = colors.label,
        shadow = Shadow(color = colors.labelHalo, offset = Offset.Zero, blurRadius = 6f),
    )
    val w = width.toDouble()
    val h = height.toDouble()

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(w, h) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    onTransform { current ->
                        var next = current.panned(pan.x.toDouble(), pan.y.toDouble(), w, h)
                        if (zoomChange != 1f) {
                            val zoom = next.zoom + ln(zoomChange.toDouble()) / ln(2.0)
                            next = next.zoomedAbout(
                                centroid.x.toDouble(),
                                centroid.y.toDouble(),
                                min(zoom, MaxZoom),
                                w,
                                h,
                            )
                        }
                        next
                    }
                }
            }
            .pointerInput(w, h) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        onTransform { current ->
                            current.zoomedAbout(tap.x.toDouble(), tap.y.toDouble(), min(current.zoom + 1.0, MaxZoom), w, h)
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawMap(data, viewport, colors, textMeasurer, labelStyle)
        }

        val cellPixels = with(density) { ClusterCellDp.dp.toPx() }.toDouble()
        val clusters = remember(media, viewport, width, height) {
            MapClusterer.cluster(media, viewport.zoom, cellPixels, viewport.visibleBox(w, h, cellPixels))
        }
        val halfBubble = with(density) { BubbleSize.toPx() } / 2
        // The bubble that was tapped: its photo grows out of it, and flies back into it when closed.
        var openedBubble by remember { mutableStateOf(-1L) }
        for (cluster in clusters) {
            key(cluster.coverId) {
                val x = viewport.screenX(cluster.longitude, w).toFloat()
                val y = viewport.screenY(cluster.latitude, h).toFloat()
                ClusterBubble(
                    cluster = cluster,
                    shared = cluster.coverId == openedBubble,
                    onClick = {
                        openedBubble = cluster.coverId
                        onClusterClick(cluster)
                    },
                    modifier = Modifier.offset { IntOffset((x - halfBubble).roundToInt(), (y - halfBubble).roundToInt()) },
                )
            }
        }
    }
}

@Composable
private fun ClusterBubble(
    cluster: GeoCluster,
    shared: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover = remember(cluster.coverId) { coverUri(cluster.coverId) }
    val sharedModifier = if (shared) Modifier.gridSharedBounds(cluster.coverId) else Modifier
    Box(modifier.size(BubbleSize).then(sharedModifier).clickable(onClick = onClick)) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
        if (cluster.count > 1) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Text(
                    text = shortCount(cluster.count),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

private fun coverUri(id: Long): String =
    ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), id).toString()

private fun shortCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 10_000 -> String.format(Locale.ROOT, "%.1fk", count / 1_000.0)
    else -> "${count / 1_000}k"
}

@Composable
private fun MapStatus(scan: LocationScanState, showEmpty: Boolean, onPermissionResult: () -> Unit) {
    when {
        scan.status == LocationScanStatus.NeedsPermission -> PermissionCard(onPermissionResult)
        scan.status == LocationScanStatus.Scanning -> StatusCard {
            val numbers = NumberFormat.getIntegerInstance()
            Text(
                text = stringResource(
                    R.string.map_scanning,
                    numbers.format(scan.scanned),
                    numbers.format(scan.total),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { if (scan.total == 0) 0f else scan.scanned.toFloat() / scan.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        showEmpty -> StatusCard {
            Text(stringResource(R.string.map_empty), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun PermissionCard(onResult: () -> Unit) {
    val context = LocalContext.current
    // After one refusal Android may not ask again, so the button leads to the app's settings instead.
    var refused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refused = !granted
        onResult()
    }
    StatusCard {
        Text(stringResource(R.string.map_permission_message), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                if (refused) openAppSettings(context) else launcher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            },
        ) {
            Text(stringResource(if (refused) R.string.map_permission_settings else R.string.map_permission_allow))
        }
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        },
    )
}

private class MapColors(
    val ocean: Color,
    val land: Color,
    val coast: Color,
    val border: Color,
    val grid: Color,
    val label: Color,
    val labelHalo: Color,
    val place: Color,
)

@Composable
private fun mapColors(): MapColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        MapColors(
            ocean = scheme.primaryContainer.copy(alpha = 0.30f).compositeOver(scheme.surface),
            land = scheme.surfaceContainerHigh,
            coast = scheme.outline.copy(alpha = 0.6f),
            border = scheme.outline.copy(alpha = 0.45f),
            grid = scheme.onSurface.copy(alpha = 0.10f),
            label = scheme.onSurface,
            labelHalo = scheme.surfaceContainerHigh,
            place = scheme.onSurfaceVariant,
        )
    }
}

// ---- drawing ----

/** Graticule spacings in degrees; the smallest one that is not too crowded is used. */
private val GraticuleSteps = doubleArrayOf(0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 45.0)
private const val MaxLabels = 220

private fun DrawScope.drawMap(
    data: MapData,
    viewport: MapViewport,
    colors: MapColors,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val w = size.width.toDouble()
    val h = size.height.toDouble()
    val worldSize = MapViewport.worldSize(viewport.zoom)
    // The world position, in pixels, of the top-left corner of the screen.
    val originX = Mercator.x(viewport.centerLongitude) * worldSize - w / 2
    val originY = Mercator.y(viewport.centerLatitude) * worldSize - h / 2

    val land = buildPath(data.land, close = true, worldSize, originX, originY, w, h)
    land.fillType = PathFillType.EvenOdd // lakes and seas inside the outlines become holes
    drawPath(land, colors.land)
    drawPath(land, colors.coast, style = Stroke(width = 0.75f * density))

    drawGraticule(viewport, worldSize, originX, originY, w, h, colors.grid)

    val borders = buildPath(data.borders, close = false, worldSize, originX, originY, w, h)
    drawPath(borders, colors.border, style = Stroke(width = 0.75f * density))

    drawPlaces(data, viewport, worldSize, originX, originY, colors, textMeasurer, labelStyle)
}

/** The outlines that touch the screen as one path. Points less than a pixel apart are skipped. */
private fun buildPath(
    lines: List<MapPolyline>,
    close: Boolean,
    worldSize: Double,
    originX: Double,
    originY: Double,
    width: Double,
    height: Double,
): Path {
    val path = Path()
    for (line in lines) {
        if (line.maxX * worldSize - originX < 0 || line.minX * worldSize - originX > width ||
            line.maxY * worldSize - originY < 0 || line.minY * worldSize - originY > height
        ) {
            continue
        }
        var lastX = 0f
        var lastY = 0f
        for (i in 0 until line.size) {
            val x = (line.xs[i] * worldSize - originX).toFloat()
            val y = (line.ys[i] * worldSize - originY).toFloat()
            if (i == 0) {
                path.moveTo(x, y)
                lastX = x
                lastY = y
            } else if (abs(x - lastX) + abs(y - lastY) >= 0.8f) {
                path.lineTo(x, y)
                lastX = x
                lastY = y
            }
        }
        if (close) path.close()
    }
    return path
}

private fun DrawScope.drawGraticule(
    viewport: MapViewport,
    worldSize: Double,
    originX: Double,
    originY: Double,
    width: Double,
    height: Double,
    color: Color,
) {
    val minSpacing = 90 * density
    val step = GraticuleSteps.firstOrNull { it / 360.0 * worldSize >= minSpacing } ?: GraticuleSteps.last()
    val visible = viewport.visibleBox(width, height)
    val stroke = 0.75f * density

    var lon = ceil(visible.west / step) * step
    while (lon <= visible.east) {
        val x = (Mercator.x(lon) * worldSize - originX).toFloat()
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
        lon += step
    }
    var lat = ceil(visible.south / step) * step
    while (lat <= visible.north) {
        if (lat > -Mercator.MAX_LATITUDE && lat < Mercator.MAX_LATITUDE) {
            val y = (Mercator.y(lat) * worldSize - originY).toFloat()
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        }
        lat += step
    }
}

private fun DrawScope.drawPlaces(
    data: MapData,
    viewport: MapViewport,
    worldSize: Double,
    originX: Double,
    originY: Double,
    colors: MapColors,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val margin = 40f * density
    val labels = ArrayList<PlaceLabel>()
    for (place in data.places) {
        // The places are listed largest first, so once one is too small for this zoom all the rest are.
        if (viewport.zoom < PlaceVisibility.minZoom(place.population)) break
        val x = (place.x * worldSize - originX).toFloat()
        val y = (place.y * worldSize - originY).toFloat()
        if (x < -margin || x > size.width + margin || y < -margin || y > size.height + margin) continue
        labels.add(PlaceLabel(textMeasurer.measure(place.name, labelStyle, maxLines = 1), x, y))
        if (labels.size >= MaxLabels) break
    }

    val dot = 3f * density
    val gap = 5f * density
    val boxes = labels.map {
        val halfHeight = it.layout.size.height / 2f
        LabelPlacer.Box(it.x - dot, it.y - halfHeight, it.x + dot + gap + it.layout.size.width, it.y + halfHeight)
    }
    val shown = LabelPlacer.place(boxes, padding = 4f * density)
    labels.forEachIndexed { i, label ->
        if (!shown[i]) return@forEachIndexed
        drawCircle(colors.place, radius = dot, center = Offset(label.x, label.y))
        drawText(label.layout, topLeft = Offset(label.x + dot + gap, label.y - label.layout.size.height / 2f))
    }
}

private class PlaceLabel(val layout: TextLayoutResult, val x: Float, val y: Float)
