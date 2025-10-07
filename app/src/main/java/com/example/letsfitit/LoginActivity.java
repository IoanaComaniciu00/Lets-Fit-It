package com.example.letsfitit;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.CreateCredentialResponse;
import androidx.credentials.CreatePasswordRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.PasswordCredential;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 100;

    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authListener;
    private EditText loginEmail, loginPassword;
    private TextView signupRedirectText, forgotPassword;
    private Button loginButton;
    private SignInButton googleSignInButton;
    private CredentialManager credentialManager;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);

        loginEmail = findViewById(R.id.login_email);
        loginPassword = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        signupRedirectText = findViewById(R.id.signUpRedirectText);
        forgotPassword = findViewById(R.id.forgot_password);
        googleSignInButton = findViewById(R.id.googleBtn);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        loginButton.setOnClickListener(view -> {
            String email = loginEmail.getText().toString();
            String pass = loginPassword.getText().toString();

            if (!email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                if (!pass.isEmpty()) {
                    signInWithEmailPassword(email, pass);
                } else {
                    loginPassword.setError("Password cannot be empty");
                }
            } else if (email.isEmpty()) {
                loginEmail.setError("Email cannot be empty");
            } else {
                loginEmail.setError("Please enter a valid email");
            }
        });

        googleSignInButton.setOnClickListener(view -> signInWithGoogle());

        signupRedirectText.setOnClickListener(view ->
                startActivity(new Intent(LoginActivity.this, SignUpActivity.class)));

        forgotPassword.setOnClickListener(view ->
                Toast.makeText(this, "Work in progress", Toast.LENGTH_SHORT).show());

        // AuthStateListener
        authListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                Log.d(TAG, "AuthStateListener - user is signed in");
                updateUI(user);
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        auth.addAuthStateListener(authListener);

        // Avoid re-authenticating if already signed in
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "User is already signed in");
            updateUI(currentUser);
        } else {
            checkGoogleOrSavedCredentials(); // Move this here
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
        }
    }

    private void checkGoogleOrSavedCredentials() {
        // Google sign-in
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            Log.d(TAG, "Found Google account, authenticating with Firebase");
            firebaseAuthWithGoogle(account.getIdToken());
            return;
        }

        // Credential Manager
        Log.d(TAG, "Checking Credential Manager for saved credentials...");
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new androidx.credentials.GetPasswordOption())
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                Runnable::run,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential credential = result.getCredential();
                        if (credential instanceof PasswordCredential) {
                            PasswordCredential passwordCredential = (PasswordCredential) credential;
                            performFirebaseEmailSignIn(passwordCredential.getId(), passwordCredential.getPassword());
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (!(e instanceof NoCredentialException)) {
                            Log.e(TAG, "Credential Manager error", e);
                        }
                    }
                });
    }

    private void signInWithEmailPassword(String email, String password) {
        performFirebaseEmailSignIn(email, password);
    }

    private void performFirebaseEmailSignIn(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Email/password login successful");
                        saveCredentialsToManager(email, password);
                        updateUI(auth.getCurrentUser());
                    } else {
                        Log.e(TAG, "Email/password login failed", task.getException());
                        Toast.makeText(LoginActivity.this,
                                "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveCredentialsToManager(String email, String password) {
        CreatePasswordRequest request = new CreatePasswordRequest(email, password);
        credentialManager.createCredentialAsync(
                this,
                request,
                null,
                Runnable::run,
                new CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>() {
                    @Override
                    public void onResult(CreateCredentialResponse response) {
                        Log.d(TAG, "Credentials saved to Credential Manager");
                    }

                    @Override
                    public void onError(CreateCredentialException e) {
                        Log.e(TAG, "Failed to save credentials", e);
                    }
                }
        );
    }

    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign-in failed", e);
                Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Google sign-in failed", task.getException());
                        Toast.makeText(this, "Google authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            Log.d(TAG, "Updating UI for: " + user.getEmail());
            Toast.makeText(this, "Sign in successful", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }
}
