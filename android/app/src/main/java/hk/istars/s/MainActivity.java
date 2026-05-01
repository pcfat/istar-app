package hk.istars.s;

import android.os.Bundle;
import android.os.Handler;
import android.webkit.WebView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.getcapacitor.BridgeActivity;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends BridgeActivity {

    private SwipeRefreshLayout swipeRefreshLayout;

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
                if (webView != null) webView.reload();
                swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 1500);
            });
        }

        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) return;
                final String token = task.getResult().replace("'", "\\'");
                Handler handler = new Handler(getMainLooper());
                Runnable inject = new Runnable() {
                    int attempts = 0;
                    @Override
                    public void run() {
                        attempts++;
                        WebView webView = getBridge().getWebView();
                        if (webView != null) {
                            String js = "if(typeof window.__registerFCMToken==='function'){window.__registerFCMToken('" + token + "');}";
                            webView.evaluateJavascript(js, result -> {
                                if ((result == null || result.equals("null") || result.equals("undefined")) && attempts < 15) {
                                    handler.postDelayed(this, 2000);
                                }
                            });
                        }
                    }
                };
                handler.postDelayed(inject, 5000);
            });
    }
}