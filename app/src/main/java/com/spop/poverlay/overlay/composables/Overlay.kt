package com.spop.poverlay.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spop.poverlay.overlay.composables.OverlayMainContent
import com.spop.poverlay.overlay.composables.OverlayMinimizedContent
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

//This is the percentage of the overlay that is offscreen when hidden
const val PercentVisibleWhenHidden = .00f
const val VisibilityChangeDuration = 150
val OverlayCornerRadius = 25.dp
val StatCardWidth = 105.dp
val PowerChartFullWidth = 200.dp
val PowerChartShrunkWidth = 120.dp
val BackgroundColorDefault = Color(20, 20, 20)

@Composable
fun Overlay(
    sensorViewModel: OverlaySensorViewModel,
    timerViewModel: OverlayTimerViewModel,
    height: Dp,
    locationState: State<OverlayLocation>,
    horizontalDragCallback: (Float) -> Float,
    verticalDragCallback: (Float) -> Float,
    offsetCallback: (Float, Float) -> Unit,
    onLayout: (IntSize) -> Unit
) {
    val placeholderText = "-"

    val power by sensorViewModel.powerValue.collectAsStateWithLifecycle(initialValue = placeholderText)

    val powerGraph = remember { sensorViewModel.powerGraph }
    val rpm by sensorViewModel.rpmValue.collectAsStateWithLifecycle(initialValue = placeholderText)
    val resistance by sensorViewModel.resistanceValue.collectAsStateWithLifecycle(initialValue = placeholderText)
    val speed by sensorViewModel.speedValue.collectAsStateWithLifecycle(initialValue = placeholderText)
    val speedLabel by sensorViewModel.speedLabel.collectAsStateWithLifecycle(initialValue = "")
    val timerLabel by timerViewModel.timerLabel.collectAsStateWithLifecycle(initialValue = "")
    val isTimerPaused by timerViewModel.timerPaused.collectAsStateWithLifecycle(initialValue = false)
    val errorMessage by sensorViewModel.errorMessage.collectAsStateWithLifecycle(initialValue = null)

    var pauseChart by remember { mutableStateOf(true) }

    val visibleFlow = remember {
        sensorViewModel.isMinimized.onEach {
            // If the visibility change used an animator, pause the graph
            // This improves animation performance drastically
            val isAnimatedVisibilityChange = pauseChart != it
            pauseChart = isAnimatedVisibilityChange
        }
    }

    val minimized by visibleFlow.collectAsStateWithLifecycle(initialValue = false)
    val location by remember { locationState }
    val size = remember { mutableStateOf(IntSize.Zero) }


    val maxOffset = with(LocalDensity.current) {
        (height * (1 - PercentVisibleWhenHidden)).roundToPx()
    }

    val timerAlpha by animateFloatAsState(
        if (minimized) .5f else 1f,
        animationSpec = TweenSpec(VisibilityChangeDuration, 0, LinearEasing)
    )

    val visibilityOffset by animateIntOffsetAsState(
        if (minimized) {
            when (location) {
                OverlayLocation.Top -> IntOffset(0, -maxOffset)
                OverlayLocation.Bottom -> IntOffset(0, maxOffset)
            }
        } else {
            IntOffset.Zero
        },
        animationSpec = TweenSpec(VisibilityChangeDuration, 0, LinearEasing),
        finishedListener = {
            pauseChart = false
        }
    )

    offsetCallback(visibilityOffset.y.toFloat(), size.value.height.toFloat())

    var horizontalDragOffset by remember { mutableStateOf(0f) }
    var verticalDragOffset by remember { mutableStateOf(0f) }

    val backgroundShape = when (location) {
        OverlayLocation.Top -> RoundedCornerShape(
            bottomStart = OverlayCornerRadius, bottomEnd = OverlayCornerRadius
        )
        OverlayLocation.Bottom -> RoundedCornerShape(
            topStart = OverlayCornerRadius, topEnd = OverlayCornerRadius
        )
    }
    val timer = @Composable {
        val showTimerWhenMinimizedFlow = remember {
            timerViewModel.showTimerWhenMinimized.onEach {
                Timber.i("Show Timer: $it")
            }
        }
        val showTimerWhenMinimized by showTimerWhenMinimizedFlow
            .collectAsStateWithLifecycle(initialValue = true)

        OverlayMinimizedContent(
            isMinimized = minimized,
            timerPaused = isTimerPaused,
            showTimerWhenMinimized = showTimerWhenMinimized,
            location = location,
            powerLabel = power,
            contentAlpha = timerAlpha,
            timerLabel = timerLabel,
            cadenceLabel = rpm,
            speedLabel = speed,
            resistanceLabel = resistance,
            onTap = { timerViewModel.onTimerTap() },
            onLongPress = { timerViewModel.onTimerLongPress() }
        )
    }
    val mainContent = @Composable {
        Box(modifier = Modifier
            .requiredHeight(height)
            .wrapContentWidth(unbounded = true)
            .onSizeChanged {
                if (it.width != size.value.width || it.height != size.value.height) {
                    size.value = it
                    onLayout(size.value)
                }
            }
            .background(
                color = BackgroundColorDefault,
                shape = backgroundShape,
            )
            .pointerInput(Unit) {
                detectDragGestures(onDrag = { change, offset ->
                    change.consume()
                    horizontalDragOffset += offset.x
                    horizontalDragOffset = horizontalDragCallback(horizontalDragOffset)

                    verticalDragOffset += offset.y
                    verticalDragOffset = verticalDragCallback(verticalDragOffset)
                }, onDragEnd = {
                    verticalDragOffset = 0f
                })
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { sensorViewModel.onOverlayPressed() },
                    onLongPress = { sensorViewModel.onOverlayDoubleTap() })
            }) {


            val rowAlignment = when (location) {
                OverlayLocation.Top -> Alignment.Top
                OverlayLocation.Bottom -> Alignment.Bottom
            }

            OverlayMainContent(
                modifier = Modifier
                    .wrapContentWidth(unbounded = true)
                    .padding(horizontal = 9.dp)
                    .padding(bottom = 5.dp),
                rowAlignment = rowAlignment,
                power = power,
                rpm = rpm,
                pauseChart = pauseChart,
                powerGraph = powerGraph,
                resistance = resistance,
                speed = speed,
                speedLabel = speedLabel,
                onSpeedClicked = { sensorViewModel.onClickedSpeed() },
                onChartClicked = { sensorViewModel.onOverlayPressed() }
            )
        }
    }
    Column(
        modifier = Modifier
            .wrapContentSize()
            .offset { visibilityOffset },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        errorMessage?.let {
            Snackbar(
                action = {
                    Button(onClick = { sensorViewModel.onDismissErrorPressed() }) {
                        Text("Dismiss")
                    }
                },
                containerColor = Color.White,
                modifier = Modifier
                    .padding(8.dp)
                    .zIndex(1f)
            ) {
                Text(it, color = Color.Black)
            }
            return@Column
        }

        when (location) {
            OverlayLocation.Top -> {
                mainContent()
                timer()
            }
            OverlayLocation.Bottom -> {
                timer()
                mainContent()
            }
        }

    }

}
