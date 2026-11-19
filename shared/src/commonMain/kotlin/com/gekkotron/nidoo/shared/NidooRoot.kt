package com.gekkotron.nidoo.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Placeholder root of the shared app until the real screens move to commonMain. */
@Composable
fun NidooRoot() {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0E1116)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("Nidoo", style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(12.dp))
        BasicText(sharedCoreSummary(), style = TextStyle(color = Color(0xFFB8C0CC), fontSize = 16.sp))
    }
}
