package hk.istars.s;

import android.os.Bundle;
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
                if (webView != null) {
                    webView.reload();
                }
                swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 1500);
            });
        }

        // Get FCM token and pass to WebView
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) return;
                String token = task.getResult();
                if (token == null) return;

                // Inject token into WebView for server registration
                getBridge().getWebView().post(() -> {
                    String js = "if(window.__registerFCMToken)window.__registerFCMToken('" + token.replace("'", "\\'") + "');";
                    getBridge().getWebView().evaluateJavascript(js, null);
                });
            });
    }
}
