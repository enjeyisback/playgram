package app.playgram.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Pink = Color(0xFFFF4FD8)
private val Violet = Color(0xFF8B3DFF)
private val Cyan = Color(0xFF22D3EE)
private val Night = Color(0xFF07070F)

private fun outerPath() = Path().apply {
    moveTo(31.4f, 16.3f); quadraticBezierTo(20f, 10f, 20f, 23f)
    lineTo(20f, 77f); quadraticBezierTo(20f, 90f, 31.4f, 83.7f)
    lineTo(80.6f, 56.3f); quadraticBezierTo(92f, 50f, 80.6f, 43.7f); close()
}

private fun innerPath() = Path().apply {
    moveTo(43.1f, 35.1f); quadraticBezierTo(38f, 32f, 38f, 38f)
    lineTo(38f, 62f); quadraticBezierTo(38f, 68f, 43.1f, 64.9f)
    lineTo(62.9f, 53.1f); quadraticBezierTo(68f, 50f, 62.9f, 46.9f); close()
}

/** Launch animation: the play shape is traced, fills in, then the name and tagline fade in. */
@Composable
fun PlaygramSplash(onFinished: () -> Unit) {
    val trace = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { glow.animateTo(1f, tween(1400, easing = LinearEasing)) }
            launch { trace.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
            launch { delay(800); fill.animateTo(1f, tween(500)) }
            launch { delay(1250); word.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            launch { delay(1600); tag.animateTo(1f, tween(500)) }
        }
        delay(700)
        exit.animateTo(0f, tween(300))
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().alpha(exit.value).background(Night),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Violet.copy(alpha = 0.45f * glow.value), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.95f),
                    radius = size.width * 0.9f
                )
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawMark(trace.value, fill.value) }
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(app.playgram.R.drawable.logo_mark), null,
                    Modifier.fillMaxSize().alpha(fill.value)
                        .graphicsLayer { val k = 0.92f + 0.08f * fill.value; scaleX = k; scaleY = k }
                )
            }
            Box(Modifier.height(10.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Play") }
                    withStyle(SpanStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFA855FF), Color(0xFF7C3AED))))) { append("gram") }
                },
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.alpha(word.value)
            )
            Box(Modifier.height(8.dp))
            Text(
                "YOUR LIBRARY. YOUR CINEMA.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                letterSpacing = 3.sp,
                fontFamily = Manrope,
                modifier = Modifier.alpha(tag.value)
            )
        }
    }
}

private fun DrawScope.drawMark(trace: Float, fill: Float) {
    val s = size.minDimension / 100f
    val brush = Brush.linearGradient(
        listOf(Pink, Violet, Cyan), start = Offset(20f, 10f), end = Offset(92f, 90f)
    )
    withTransform({ scale(s, s, pivot = Offset.Zero) }) {
        val outer = outerPath()
        if (fill < 1f) {
            val seg = Path()
            val m = PathMeasure().apply { setPath(outer, true) }
            m.getSegment(0f, m.length * trace, seg, true)
            drawPath(seg, brush, alpha = 0.25f, style = Stroke(8.4f, cap = StrokeCap.Round))
            drawPath(seg, brush, style = Stroke(2.4f, cap = StrokeCap.Round))
        }
    }
}
