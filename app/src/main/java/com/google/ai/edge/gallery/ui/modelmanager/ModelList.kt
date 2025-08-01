package com.google.ai.edge.gallery.ui.modelmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.ClickableLink
import com.google.ai.edge.gallery.ui.common.modelitem.ModelItem
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

private const val TAG = "AGModelList"

/** The list of models in the model manager. */
@Composable
fun ModelList(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  contentPadding: PaddingValues,
  onModelClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
  account: GoogleSignInAccount? = null,  // <-- Added here
) {
  // This is just to update "models" list when task.updateTrigger is updated so that the UI can
  // be properly updated.
  val models by
  remember(task) {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.toList().filter { !it.imported }
      } else {
        listOf()
      }
    }
  }
  val importedModels by
  remember(task) {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.toList().filter { it.imported }
      } else {
        listOf()
      }
    }
  }

  val listState = rememberLazyListState()

  Box(contentAlignment = Alignment.BottomEnd) {
    LazyColumn(
      modifier = modifier.padding(top = 8.dp),
      contentPadding = contentPadding,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      state = listState,
    ) {
      // Headline.
      item(key = "headline") {
        Text(
          task.description,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
      }

      // URLs.
      item(key = "urls") {
        Row(
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        ) {
          Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
//            if (task.docUrl.isNotEmpty()) {
//              ClickableLink(
//                url = task.docUrl,
//                linkText = "API Documentation",
//                icon = Icons.Outlined.Description,
//              )
//            }
//            if (task.sourceCodeUrl.isNotEmpty()) {
//              ClickableLink(
//                url = task.sourceCodeUrl,
//                linkText = "Example code",
//                icon = Icons.Outlined.Code,
//              )
//            }
          }
        }
      }

      // List of models within a task.
      items(items = models) { model ->
        Box {
          ModelItem(
            model = model,
            task = task,
            modelManagerViewModel = modelManagerViewModel,
            onModelClicked = onModelClicked,
            modifier = Modifier.padding(horizontal = 12.dp),
            // You can optionally pass account here to ModelItem if you extend it:
            // account = account,
          )
        }
      }

      // List of imported models within a task.
      items(items = importedModels, key = { it.name }) { model ->
        Box {
          ModelItem(
            model = model,
            task = task,
            modelManagerViewModel = modelManagerViewModel,
            onModelClicked = onModelClicked,
            modifier = Modifier.padding(horizontal = 12.dp),
            // account = account,
          )
        }
      }
    }
  }
}
