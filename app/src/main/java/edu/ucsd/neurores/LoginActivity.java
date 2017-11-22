package edu.ucsd.neurores;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("CommitPrefEdits")

public class LoginActivity extends AppCompatActivity{

    public static final String PREFS = "login_prefs";
    public static final String TOKEN = "neuro_res_token";
    public static final String NAME = "neuro_res_name";
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    // UI references.
    private EditText mEmailView;
    private EditText mPasswordView;
    private Button mEmailSignInButton;

    private boolean loginInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loginInProgress = false;


        setContentView(R.layout.activity_login);

        if( !isConnectedToNetwork()){
            showNoInternetConnectionToast();
        }
        // Set up the login form.
        mEmailView = (EditText) findViewById(R.id.email);
        mEmailView.requestFocus();

        mEmailView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_NEXT) {
                    mPasswordView.requestFocus();
                    return true;
                }
                return false;
            }
        });

        mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        showLastUserSignedIn();
        removeToken();
        showToast(getString(R.string.hippa_statement), this);
    }

    private void showLastUserSignedIn() {
        String lastUserEmail = getSavedEmail();
        if(lastUserEmail != null){
            mEmailView.setText(lastUserEmail);
        }
    }

    private void showNoInternetConnectionToast() {
        showToast(getResources().getString(R.string.no_internet_connection_login), this);
    }

    private void showToast(final String message, final Context context){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message , Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isConnectedToNetwork() {
        final ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();

        return activeNetwork != null && activeNetwork.isConnected();
    }

    public void checkServerIsOnline(RequestWrapper.OnHTTPRequestCompleteListener onCompleteListener){
        RequestWrapper.checkServerIsOnline(this,  onCompleteListener);
    }

    private void removeToken(){
        SharedPreferences sp = LoginActivity.this.getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.remove(LoginActivity.TOKEN);
        //editor.remove(LoginActivity.NAME);
        editor.commit();
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        final Context context = this;
        if(! isConnectedToNetwork()){
            showNoInternetConnectionToast();
            return;
        }

        checkServerIsOnline(new RequestWrapper.OnHTTPRequestCompleteListener() {

            @Override
            public void onComplete(String s) {
                actuallyLogin();
            }

            @Override
            public void onError(int i) {
                showToast(getString(R.string.cannot_connect_to_server), context);
            }
        });

    }

    private void actuallyLogin(){
        Button loginButton = (Button) findViewById(R.id.email_sign_in_button);
        loginButton.setText(R.string.signing_in);
        if (loginInProgress) {
            return;
        }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        final String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            mEmailSignInButton.setText(R.string.log_in);
            focusView.requestFocus();
        } else {

            loginInProgress = true;
            RequestWrapper.login(email, password, new RequestWrapper.OnCompleteListener() {
                @Override
                public void onComplete(String sessionToken) {
                    Log.v("taggy", "Token is: " + sessionToken);
                    saveEmail(email);
                    saveToken(sessionToken);
                    loginInProgress = false;

                    RequestWrapper.registerFirebaseToken(getApplicationContext(), sessionToken, null);

                    Intent startApp = new Intent(LoginActivity.this, MainActivity.class);
                    LoginActivity.this.startActivity(startApp);
                    finish();
                }

                @Override
                public void onError(String s) {
                    loginInProgress = false;
                    mEmailSignInButton.setText(R.string.log_in);
                    if(s.equals(LoginTask.UNAUTH_USER)){
                        mEmailView.setError(getString(R.string.error_account_not_authorized));
                        mEmailView.requestFocus();
                    }else{
                        mPasswordView.setError(getString(R.string.error_incorrect_username_or_password));
                        mPasswordView.requestFocus();
                    }
                }
            });

        }
    }

    private boolean isEmailValid(String email) {
        //TODO: Replace this with your own logic
        //return email.contains("@");
        return true;
    }

    private boolean isPasswordValid(String password) {
        //TODO: Replace this with your own logic
        return true;
        //return password.length() > 4;
    }

    private String getSavedEmail(){
        SharedPreferences sPref = getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        return sPref.getString(LoginActivity.NAME, null);
    }


    private void saveEmail(String email){
        SharedPreferences sp = LoginActivity.this.getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(LoginActivity.NAME , email);
        editor.commit();
    }

    private void saveToken(String loginToken){
        SharedPreferences sp = LoginActivity.this.getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(LoginActivity.TOKEN, loginToken);
        editor.commit();
    }
}

