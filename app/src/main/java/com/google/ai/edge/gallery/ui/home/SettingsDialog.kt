package com.google.ai.edge.gallery.ui.home
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.proto.Theme
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.firebase.auth.FirebaseUser
import coil.compose.AsyncImage


private val THEME_OPTIONS = listOf(Theme.THEME_AUTO, Theme.THEME_LIGHT, Theme.THEME_DARK)
@Composable
fun SettingsDialog(
  curThemeOverride: Theme,
  modelManagerViewModel: ModelManagerViewModel,
  currentUser: FirebaseUser?, // ✅ Add currentUser here
  onGoogleSignInClicked: () -> Unit, // ✅ Add Google Sign-In callback
  onSignOut: () -> Unit, // ✅ Add Sign Out callback
  onDismissed: () -> Unit,

) {
  var selectedTheme by remember { mutableStateOf(curThemeOverride) }
  val interactionSource = remember { MutableInteractionSource() }
  val userName = currentUser?.displayName ?: "Guest"
  val userEmail = currentUser?.email ?: ""
  val userPhotoUrl = currentUser?.photoUrl?.toString()

  Dialog(onDismissRequest = onDismissed) {
    val focusManager = LocalFocusManager.current
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(interactionSource = interactionSource, indication = null) {
          focusManager.clearFocus()
        },
      shape = RoundedCornerShape(16.dp),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // --- If user is logged in ---
        if (currentUser != null) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
          ) {

            // 👤 Profile Info Section
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              // Profile Image
              if (userPhotoUrl != null) {
                AsyncImage(
                  model = userPhotoUrl,
                  contentDescription = "Profile Picture",
                  modifier = Modifier
                    .padding(4.dp)
                    .size(64.dp)
                    .clip(RoundedCornerShape(50))
                )
              }

              Column {
                Text(
                  text = "$userName",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = userEmail,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            // 🎨 Theme Selector
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(
                "Theme",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              )
              MultiChoiceSegmentedButtonRow {
                THEME_OPTIONS.forEachIndexed { index, theme ->
                  SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, THEME_OPTIONS.size),
                    onCheckedChange = {
                      selectedTheme = theme
                      ThemeSettings.themeOverride.value = theme
                      modelManagerViewModel.saveThemeOverride(theme)
                    },
                    checked = theme == selectedTheme,
                    label = { Text(themeLabel(theme)) },
                  )
                }
              }
            }

            // 🚪 Logout Button
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              Button(onClick = onSignOut) {
                Text("Logout")
              }
            }
          }
        }
        else {

          Text(
            text = "Welcome to LocalLLM",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )

          Text(
            text = "Sign in with Google to unlock themes and personalized features.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Button(
            onClick = onGoogleSignInClicked,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_google), // Make sure you have this icon
              contentDescription = "Google Icon",
              modifier = Modifier.padding(end = 8.dp)
            )
            Text("Sign in with Google")
          }
        }
      }
    }
  }
}

private fun themeLabel(theme: Theme): String {
  return when (theme) {
    Theme.THEME_AUTO -> "Auto"
    Theme.THEME_LIGHT -> "Light"
    Theme.THEME_DARK -> "Dark"
    else -> "Unknown"
  }
}
