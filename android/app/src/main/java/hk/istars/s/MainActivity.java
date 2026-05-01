package hk.istars.s;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends BridgeActivity {

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private WebView webView;
    private boolean isPulling = false;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {}
        );

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        webView = getBridge().getWebView();
        if (webView != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.setHorizontalScrollBarEnabled(false);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        view.loadUrl("file:///android_asset/public/error.html");
                    }
                }
            });

            // Manual pull-to-refresh using WebView touch
            webView.setOnTouchListener((v, event) -> {
                float startY = event.getY();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float currentY = event.getY();
                        float deltaY = currentY - startY;
                        // User pulling down from top of page
                        if (deltaY > 80 && isAtTop() && !isPulling) {
                            isPulling = true;
                            reloadAndReset();
                        }
                        break;
                }
                return false;
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

    private boolean isAtTop() {
        if (webView == null) return true;
        return webView.getScrollY() <= 10;
    }

    private void reloadAndReset() {
        if (webView != null) {
            webView.reload();
            // Reset pull state after 2 seconds
            new Handler(getMainLooper()).postDelayed(() -> isPulling = false, 2000);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "istar_notifications", "星進教育通知", NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("星進教育 App 通知");
            channel.enableLights(true);
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}