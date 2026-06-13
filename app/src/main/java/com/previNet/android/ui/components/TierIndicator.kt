package com.previNet.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Looks3
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.LooksTwo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.previNet.android.R
import com.previNet.android.ui.theme.LocalTierColors
import com.previNet.android.ui.theme.Spacing
import com.previNet.android.util.Haptics

/** Live annotation-quality pill + hint. Pulses (and clicks) only when the tier upgrades. */
@Composable
fun TierIndicator(tier: Int, modifier: Modifier = Modifier) {
    val tierColors = LocalTierColors.current
    val view = LocalView.current
    val scale = remember { Animatable(1f) }
    var previousTier by remember { mutableIntStateOf(tier) }

    LaunchedEffect(tier) {
        if (tier > previousTier) {
            Haptics.confirm(view)
            scale.animateTo(1.05f, tween(durationMillis = 125))
            scale.animateTo(1f, tween(durationMillis = 125))
        }
        previousTier = tier
    }

    val icon = when (tier) {
        3 -> Icons.Rounded.Looks3
        2 -> Icons.Rounded.LooksTwo
        else -> Icons.Rounded.LooksOne
    }
    val label = when (tier) {
        3 -> stringResource(R.string.tier3_label)
        2 -> stringResource(R.string.tier2_label)
        else -> stringResource(R.string.tier1_label)
    }
    val hint = when (tier) {
        3 -> stringResource(R.string.tier3_hint)
        2 -> stringResource(R.string.tier2_hint)
        else -> stringResource(R.string.tier1_hint)
    }
    val containerColor: Color
    val contentColor: Color
    val border: BorderStroke?
    when (tier) {
        3 -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            border = null
        }
        2 -> {
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            border = null
        }
        else -> {
            containerColor = Color.Transparent
            contentColor = tierColors.tier1
            border = BorderStroke(1.dp, tierColors.tier1)
        }
    }

    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = containerColor,
            contentColor = contentColor,
            border = border,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
