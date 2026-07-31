package com.shortscap.app.auth.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shortscap.app.auth.components.BrandLogoMark
import kotlinx.coroutines.delay

/**
 * Splash: fades + scales the logo in, holds briefly, then calls
 * [onSplashFinished]. Caller decides where that navigates (mock nav wires
 * it to Welcome).
 */
@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val scale = remember { Animatable(0.7f) }
    var alpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(650, easing = EaseOutBack))
    }
    LaunchedEffect(Unit) {
        // simple manual fade since alpha isn't an Animatable here
        val steps = 20
        repeat(steps) { i ->
            alpha = (i + 1) / steps.toFloat()
            delay(15)
        }
        delay(600) // hold on screen
        onSplashFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandLogoMark(
            size = 96.dp,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "ShortsCap",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.alpha(alpha)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Track Smart. Scroll Less.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(alpha)
        )
    }
}
