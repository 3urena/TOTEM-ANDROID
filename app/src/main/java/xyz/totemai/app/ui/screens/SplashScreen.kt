package xyz.totemai.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.totemai.app.ui.components.Gyroscope
import xyz.totemai.app.ui.components.TotemLogo
import xyz.totemai.app.ui.theme.AppBackground
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.TextSecondary

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val fade = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        delay(820)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
        contentAlignment = Alignment.Center
    ) {
        Gyroscope(
            Modifier
                .size(280.dp)
                .alpha(0.9f)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TotemLogo(
                Modifier
                    .size(92.dp)
                    .alpha(fade.value)
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = "TOTEM",
                style = MaterialTheme.typography.displayLarge,
                color = Lime,
                modifier = Modifier.alpha(fade.value)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "PRIVATE INTELLIGENCE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(fade.value * 0.9f)
            )
        }
    }
}
