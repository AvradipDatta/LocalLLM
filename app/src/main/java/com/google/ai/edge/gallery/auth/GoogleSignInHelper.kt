package com.google.ai.edge.gallery.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.ai.edge.gallery.R

object GoogleSignInHelper {
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly"))
            .requestIdToken(context.getString(R.string.default_web_client_id)) // <- Firebase Web Client ID
            .build()

        return GoogleSignIn.getClient(context, gso)
    }
}
