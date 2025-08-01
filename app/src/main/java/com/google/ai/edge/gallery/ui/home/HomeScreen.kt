package com.google.ai.edge.gallery.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.TaskIcon
import com.google.ai.edge.gallery.ui.common.getTaskBgColor
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.customColors
import com.google.ai.edge.gallery.ui.theme.titleMediumNarrow
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay

private const val TAG = "AGHomeScreen"

object HomeScreenDestination {
  @StringRes val titleRes = R.string.app_name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  currentUser: FirebaseUser?,
  onSignOut: () -> Unit,
  onGoogleSignInClicked: () -> Unit,
  modelManagerViewModel: ModelManagerViewModel,
  navigateToTaskScreen: (Task, GoogleSignInAccount?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  val uiState by modelManagerViewModel.uiState.collectAsState()
  var showSettingsDialog by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current

  // Get signed-in Google account
  val signedInAccount = remember {
    val account = GoogleSignIn.getLastSignedInAccount(context)
    val hasGmailScope = account != null && GoogleSignIn.hasPermissions(
      account, Scope("https://www.googleapis.com/auth/gmail.readonly")
    )
    if (hasGmailScope) account else null
  }

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      GalleryTopAppBar(
        title = stringResource(HomeScreenDestination.titleRes),
        rightAction = AppBarAction(
          actionType = AppBarActionType.APP_SETTING,
          actionFn = { showSettingsDialog = true },
        ),
        scrollBehavior = scrollBehavior,
      )
    },
  ) { innerPadding ->
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
      TaskList(
        currentUser = currentUser,
        onGoogleSignInClicked = onGoogleSignInClicked,
        tasks = uiState.tasks,
        navigateToTaskScreen = { task ->
          Log.d(TAG, "Selected task: ${task.type.label}")
          if (currentUser == null || signedInAccount == null) {
            showSettingsDialog = true
          } else {
            navigateToTaskScreen(task, signedInAccount)
          }
        },
        loadingModelAllowlist = uiState.loadingModelAllowlist,
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding,
        onUnauthenticatedClick = {
          showSettingsDialog = true
        }
      )

      SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = 32.dp))
    }
  }

  if (showSettingsDialog) {
    SettingsDialog(
      curThemeOverride = modelManagerViewModel.readThemeOverride(),
      modelManagerViewModel = modelManagerViewModel,
      currentUser = currentUser,
      onGoogleSignInClicked = {
        showSettingsDialog = false
        onGoogleSignInClicked() // Trigger proper Gmail-scoped Google login
      },
      onSignOut = {
        onSignOut()
        showSettingsDialog = false
      },
      onDismissed = { showSettingsDialog = false },
    )
  }
}

// --------------- Rest of your unchanged code --------------- //

@Composable
private fun TaskList(
  currentUser: FirebaseUser?,
  onGoogleSignInClicked: () -> Unit,
  tasks: List<Task>,
  navigateToTaskScreen: (Task) -> Unit,
  loadingModelAllowlist: Boolean,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  onUnauthenticatedClick: () -> Unit,
) {
  val density = LocalDensity.current
  val windowInfo = LocalWindowInfo.current
  val screenWidthDp = remember { with(density) { windowInfo.containerSize.width.toDp() } }
  val screenHeightDp = remember { with(density) { windowInfo.containerSize.height.toDp() } }
  val sizeFraction = remember { ((screenWidthDp - 360.dp) / (410.dp - 360.dp)).coerceIn(0f, 1f) }

  val introText = buildAnnotatedString { append("Welcome to LocalLLM") }

  Box(modifier = modifier.fillMaxSize()) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(count = 2),
      contentPadding = contentPadding,
      modifier = modifier.padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "headline", span = { GridItemSpan(2) }) {
        Text(
          introText,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
          modifier = Modifier.padding(bottom = 20.dp).padding(horizontal = 16.dp),
        )
      }

      if (loadingModelAllowlist) {
        item(key = "loading", span = { GridItemSpan(2) }) {
          Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
          ) {
            CircularProgressIndicator(
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
              strokeWidth = 3.dp,
              modifier = Modifier.padding(end = 8.dp).size(20.dp),
            )
            Text("Loading model list...", style = MaterialTheme.typography.bodyMedium)
          }
        }
      } else {
        items(tasks) { task ->
          TaskCard(
            sizeFraction = sizeFraction,
            task = task,
            onClick = {
              if (currentUser == null) {
                onUnauthenticatedClick()
              } else {
                navigateToTaskScreen(task)
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(1f),
          )
        }
      }

      item(key = "bottomPadding", span = { GridItemSpan(2) }) {
        Spacer(modifier = Modifier.height(60.dp))
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(screenHeightDp * 0.25f)
        .background(
          Brush.verticalGradient(colors = MaterialTheme.customColors.homeBottomGradient)
        )
        .align(Alignment.BottomCenter)
    )
  }
}

@Composable
private fun TaskCard(
  task: Task,
  onClick: () -> Unit,
  sizeFraction: Float,
  modifier: Modifier = Modifier,
) {
  val padding = (24 - 18) * sizeFraction + 18
  val radius = (43.5 - 30) * sizeFraction + 30
  val iconSize = (56 - 50) * sizeFraction + 50

  val modelCount by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) task.models.size else 0
    }
  }
  val modelCountLabel by remember {
    derivedStateOf {
      when (modelCount) {
        1 -> "1 Model"
        else -> "%d Models".format(modelCount)
      }
    }
  }
  var curModelCountLabel by remember { mutableStateOf("") }
  var modelCountLabelVisible by remember { mutableStateOf(true) }
  val modelCountAlpha: Float by animateFloatAsState(
    targetValue = if (modelCountLabelVisible) 1f else 0f,
    animationSpec = tween(durationMillis = 250),
  )
  val modelCountScale: Float by animateFloatAsState(
    targetValue = if (modelCountLabelVisible) 1f else 0.7f,
    animationSpec = tween(durationMillis = 250),
  )

  LaunchedEffect(modelCountLabel) {
    if (curModelCountLabel.isEmpty()) {
      curModelCountLabel = modelCountLabel
    } else {
      modelCountLabelVisible = false
      delay(250)
      curModelCountLabel = modelCountLabel
      modelCountLabelVisible = true
    }
  }

  Card(
    modifier = modifier.clip(RoundedCornerShape(radius.dp)).clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = getTaskBgColor(task = task)),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(padding.dp)) {
      TaskIcon(task = task, width = iconSize.dp)
      Spacer(modifier = Modifier.weight(2f))
      Text(task.type.label, color = MaterialTheme.colorScheme.primary,
        style = titleMediumNarrow.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
      Spacer(modifier = Modifier.weight(1f))
      Text(
        curModelCountLabel,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.alpha(modelCountAlpha).scale(modelCountScale),
      )
    }
  }
}

fun getFileName(context: Context, uri: Uri): String? {
  if (uri.scheme == "content") {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1) {
          return cursor.getString(nameIndex)
        }
      }
    }
  } else if (uri.scheme == "file") {
    return uri.lastPathSegment
  }
  return null
}
