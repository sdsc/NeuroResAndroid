package edu.ucsd.neurores.activites;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import edu.ucsd.neurores.R;
import edu.ucsd.neurores.network.RequestWrapper;

public class WebViewLoginActivity extends AppCompatActivity {
    String loginURL = "https://neurores.ucsd.edu/token/tokenGenerator.php";
    String tokenURL = "https://neurores.ucsd.edu/key/";
    String unauthorizedURL = "https://neurores.ucsd.edu/token/unathorized";

    WebView webView;
    LinearLayout unauthorizedErrorLinearLayout;
    Button tryAgainButton;
    TextView waitTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
        setContentView(R.layout.activity_web_view_login);

        setupView();

        webView.loadUrl(loginURL);
    }

    private void setupView() {
        webView = (WebView) findViewById(R.id.login_webview);
        unauthorizedErrorLinearLayout = (LinearLayout) findViewById(R.id.unauthorized_error_linear_layout);
        tryAgainButton = (Button) findViewById(R.id.try_again_button);
        waitTextView = (TextView) findViewById(R.id.wait_message_text_view);

        tryAgainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide(unauthorizedErrorLinearLayout);
                show(waitTextView);

                clearAllWebViewData();
                webView.loadUrl(loginURL);
            }
        });

        setupWebView();
    }

    private void setupActionBar() {
        ActionBar actionbar = getSupportActionBar();
        if(actionbar != null){
            actionbar.setDisplayHomeAsUpEnabled(true);
            actionbar.setTitle(getString(R.string.action_sign_in_short));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        onBackPressed();

        return super.onOptionsItemSelected(item);
    }

    private void setupWebView() {
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url){
                hide(waitTextView);
                show(webView);
                if(url.startsWith(tokenURL)){
                    String token = extractToken(url);
                    saveTokenToPreferences(token);
                    RequestWrapper.registerFirebaseToken(getApplicationContext(), token, null);

                    startMainActivity();
                    finish();
                }else if(url.startsWith(unauthorizedURL)){
                    notifyUserUnauthorized();
                }

            }
        });

        webView.getSettings().setJavaScriptEnabled(true);
        clearAllWebViewData();
    }

    private void clearAllWebViewData(){
        webView.clearCache(true);
        webView.clearHistory();
        clearCookies(this);
    }

    private void notifyUserUnauthorized() {
        show(unauthorizedErrorLinearLayout);
        hide(webView);
    }

    private String extractToken(String url){
        return url.substring(url.indexOf(tokenURL) + tokenURL.length());
    }

    private void saveTokenToPreferences(String loginToken){
        SharedPreferences sp = WebViewLoginActivity.this.getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(LoginActivity.TOKEN, loginToken);
        editor.commit();
    }

    private void startMainActivity(){
        Intent startApp = new Intent(WebViewLoginActivity.this, MainActivity.class);
        WebViewLoginActivity.this.startActivity(startApp);
    }

    private void hide(View v){
        v.setVisibility(View.GONE);
    }

    private void show(View v){
        v.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    public static void clearCookies(Context context)
    {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else
        {
            CookieSyncManager cookieSyncMngr=CookieSyncManager.createInstance(context);
            cookieSyncMngr.startSync();
            CookieManager cookieManager=CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncMngr.stopSync();
            cookieSyncMngr.sync();
        }
    }
}
