package com.dshmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshmobile.ui.theme.IosGlass

/** iOS large title, with safe-area-friendly top padding handled by the caller. */
@Composable
fun LargeTitle(text: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text = text, style = MaterialTheme.typography.displayLarge)
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * Liquid-Glass-inspired card, adapted to Compose.
 * Translucent surface + specular top-edge highlight (a vertical
 * gradient from bright to transparent) + soft shadow + 24dp radius.
 * No native iOS blur API is claimed or used.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = IosGlass.shadow(dark), ambientColor = IosGlass.shadow(dark)),
        shape = RoundedCornerShape(24.dp),
        color = IosGlass.surface(dark),
        border = BorderStroke(1.dp, IosGlass.border(dark)),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(IosGlass.specular(dark), Color.Transparent),
                    startY = 0f, endY = 60f,
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

/** Backwards-compatible name used by earlier screens. */
@Composable
fun IosCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = GlassCard(modifier, content)

/** 44dp+ touch target, iOS blue, pill-ish 14dp radius button. */
@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Text field at 16sp+ so fields never look cramped/zoomed. */
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp),
        singleLine = singleLine,
        textStyle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun VerticalSpacer(heightDp: Int = 8) {
    Spacer(modifier = Modifier.height(heightDp.dp))
}

/** Styled empty state in the same glass language. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun LoadingState(label: String = "Loading…") {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun ErrorState(message: String) {
    GlassCard {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
