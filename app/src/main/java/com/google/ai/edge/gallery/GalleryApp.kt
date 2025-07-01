

package com.google.ai.edge.gallery

import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.ui.navigation.GalleryNavHost
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseUser

/** Top level composable representing the main screen of the application. */
@Composable
fun GalleryApp(
  currentUser: FirebaseUser? = null,
  onGoogleSignInClicked: () -> Unit = {},
  onSignOut: () -> Unit = {},
  navController: NavHostController = rememberNavController(),
) {
  GalleryNavHost(
    navController = navController,
    currentUser = currentUser,
    onGoogleSignInClicked = onGoogleSignInClicked,
    onSignOut = onSignOut,
  )
}




