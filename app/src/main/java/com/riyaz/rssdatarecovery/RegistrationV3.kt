package com.riyaz.rssdatarecovery

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun RegistrationV3(done: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val transition = rememberInfiniteTransition(label = "welcomeBackground")
    val drift1 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7000), RepeatMode.Reverse), label = "drift1")
    val drift2 by transition.animateFloat(1f, 0f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "drift2")
    val canContinue = name.trim().length > 1 && email.trim().contains("@") && email.trim().contains(".")

    Box(Modifier.fillMaxSize().background(Color(0xFF070707))) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF4A3600).copy(alpha = 0.62f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(180f + drift1 * 220f, 120f + drift2 * 180f),
                radius = 520f
            )
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFFD8A928).copy(alpha = 0.22f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(850f - drift2 * 260f, 1050f - drift1 * 240f),
                radius = 470f
            )
        ))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(Color(0xFF17130A)), Alignment.Center) { BrandV2() }
            Spacer(Modifier.height(22.dp))
            Text("Welcome to", color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.titleMedium)
            Text("RSS Data Recovery", color = Color(0xFFFFD76A), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Recover what matters.\nLet's set up your recovery profile.", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.09f))) {
                Column(Modifier.padding(20.dp)) {
                    Text("Create your profile", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Your email becomes your unique RSS user ID.", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Full name") },
                        placeholder = { Text("Enter your name") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFD76A), unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                            focusedLabelColor = Color(0xFFFFD76A), unfocusedLabelColor = Color.White.copy(alpha = 0.65f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFFFFD76A),
                            focusedLeadingIconColor = Color(0xFFFFD76A), unfocusedLeadingIconColor = Color.White.copy(alpha = 0.60f)
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email address") },
                        placeholder = { Text("you@example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFD76A), unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                            focusedLabelColor = Color(0xFFFFD76A), unfocusedLabelColor = Color.White.copy(alpha = 0.65f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFFFFD76A),
                            focusedLeadingIconColor = Color(0xFFFFD76A), unfocusedLeadingIconColor = Color.White.copy(alpha = 0.60f)
                        )
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { done(name.trim(), email.trim()) },
                        modifier = Modifier.fillMaxWidth().height(54.dp), enabled = canContinue,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC83D), contentColor = Color(0xFF191300))
                    ) {
                        Icon(Icons.Default.ArrowForward, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Continue to RSS Data Recovery", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Secure profile setup • Razeen Secure Solution", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
