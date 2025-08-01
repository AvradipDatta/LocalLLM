package com.google.ai.edge.gallery.ui.modelmanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/** A screen to manage models. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManager(
  task: Task,
  viewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  onModelClicked: (Model) -> Unit,
  account: GoogleSignInAccount?, // <-- Add this
  modifier: Modifier = Modifier,
) {
  var title = "${task.type.label} model"
  if (task.models.size != 1) title += "s"

  val modelCount by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) task.models.size else -1
    }
  }

  LaunchedEffect(modelCount) {
    if (modelCount == 0) navigateUp()
  }

  BackHandler { navigateUp() }

  Scaffold(
    modifier = modifier,
    topBar = {
      GalleryTopAppBar(
        title = title,
        leftAction = AppBarAction(
          actionType = AppBarActionType.NAVIGATE_UP,
          actionFn = navigateUp
        ),
      )
    },
  ) { innerPadding ->
    ModelList(
      task = task,
      modelManagerViewModel = viewModel,
      contentPadding = innerPadding,
      onModelClicked = onModelClicked,
      modifier = Modifier.fillMaxSize(),
      account = account, // <-- Pass account to ModelList
    )
  }
}
