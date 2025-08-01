package com.google.ai.edge.gallery

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private lateinit var googleSignInClient: GoogleSignInClient
  private lateinit var launcher: ActivityResultLauncher<Intent>
  private val firebaseAuth by lazy { FirebaseAuth.getInstance() }

  companion object {
    private const val TAG = "MainActivity"
  }

  private var user by mutableStateOf<FirebaseUser?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Request ID token, email, plus Gmail read + send scopes:
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestIdToken("44323401952-3uqmc95df1agqr1lknfsirhg4bqc13rm.apps.googleusercontent.com")
      .requestEmail()
      .requestScopes(Scope(GmailScopes.GMAIL_READONLY), Scope(GmailScopes.GMAIL_SEND))
      .build()

    googleSignInClient = GoogleSignIn.getClient(this, gso)

    launcher = registerForActivityResult(StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK && result.data != null) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
          val account = task.getResult(ApiException::class.java)
          val idToken = account?.idToken
          if (idToken == null) {
            Log.e(TAG, "Google ID token is null")
            return@registerForActivityResult
          }
          firebaseAuthWithGoogle(idToken)
        } catch (e: ApiException) {
          Log.e(TAG, "Google sign in failed", e)
        }
      } else {
        Log.w(TAG, "Google sign in canceled or failed")
      }
    }

    // Initialize user
    user = firebaseAuth.currentUser

    firebaseAuth.addAuthStateListener { auth ->
      user = auth.currentUser
    }

    setContent {
      val currentUserState = remember { mutableStateOf(firebaseAuth.currentUser) }

      // Listen to auth changes
      firebaseAuth.addAuthStateListener { auth ->
        currentUserState.value = auth.currentUser
      }

      GalleryTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          GalleryApp(
            currentUser = currentUserState.value,
            onGoogleSignInClicked = {
              // Close settings modal before opening login
              currentUserState.value = null // Optional: trigger recomposition
              startGoogleSignIn()
            },
            onSignOut = {
              firebaseAuth.signOut()
              currentUserState.value = null
              Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            }
          )
        }
      }
    }
  }

  private fun startGoogleSignIn() {
    // Sign out to force the account chooser every time
    googleSignInClient.signOut().addOnCompleteListener {
      launcher.launch(googleSignInClient.signInIntent)
    }
  }

  private fun firebaseAuthWithGoogle(idToken: String) {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    firebaseAuth.signInWithCredential(credential)
      .addOnCompleteListener(this) { task ->
        if (task.isSuccessful) {
          Log.d(TAG, "signInWithCredential:success")
          val user = firebaseAuth.currentUser
          this.user = user

          Toast.makeText(this, "Signed in successfully", Toast.LENGTH_SHORT).show()
        } else {
          Log.e(TAG, "signInWithCredential:failure", task.exception)
        }
      }
  }
}
