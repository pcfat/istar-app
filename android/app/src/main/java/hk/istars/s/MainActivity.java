package hk.istars.s;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.getcapacitor.BridgeActivity;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends BridgeActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private String pendingFcmToken = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_blue_light
            );

            swipeRefreshLayout.setOnRefreshListener(() -> {
                WebView webView = getBridge().getWebView();
                if (webView != null) {
                    webView.reload();
                }
                swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 1500);
            });
        }

        // Get FCM token
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) return;
                pendingFcmToken = task.getResult();
                injectFcmToken();
            });

        // Set WebViewClient to inject token after page loads
        getBridge().getWebView().setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectFcmToken();
            }
        });
    }

    private void injectFcmToken() {
        if (pendingFcmToken == null) return;
        WebView webView = getBridge().getWebView();
        if (webView == null) return;
        String token = pendingFcmToken.replace("'", "\\'");
        String js = "if(typeof window.__registerFCMToken === 'function') { window.__registerFCMToken('" + token + "'); }";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }
}
