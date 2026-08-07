package com.shortscap.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * The single global text-cursor (caret) configuration for every ShortsCap
 * text input.
 *
 * BasicTextField's default caret is opaque black, which is nearly invisible
 * on ShortsCap's dark surfaces — the root cause of "missing / unclear
 * blinking cursor" reports. [ScCursorColor] reads the active ShortsCap
 * accent instead, so the caret is clearly visible in Dark, Light and System
 * themes, matches the app's blue identity, and stays aligned with the text
 * baseline and the selected global font.
 *
 * Blink timing, thickness and positioning are Android's platform-standard
 * (Compose's built-in cursor animation) and therefore identical in every
 * field; only the color is centralized here. Every input in the app uses
 * this single definition — auth fields, search bars, website URL / name
 * fields, feedback, bug report, profile and any future field.
 */
@Composable
fun ScCursorColor(): Color = LocalScColors.current.Accent

/** Solid-color [cursorBrush] for BasicTextField / OutlinedTextField. */
@Composable
fun ScCursorBrush(): SolidColor = SolidColor(ScCursorColor())
