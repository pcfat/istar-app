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
    private float touchStartY = 0;
    private boolean isAtTop = true;
    private long lastPullTime = 0;

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

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    // Only handle real errors, not our internal reload URL
                    if (request.isForMainFrame() && !request.getUrl().toString().startsWith("istar://")) {
                        view.loadUrl("file:///android_asset/public/error.html");
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    // Handle our pull-to-refresh URL scheme
                    if ("istar://refresh".equals(url)) {
                        view.reload();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    injectPullRefreshScript(view);
                    injectFcmToken(view);
                }
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            touchStartY = ev.getY();
        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
            float deltaY = ev.getY() - touchStartY;
            long now = System.currentTimeMillis();
            // Only trigger if: pulled down >150px, was at top, and 2s have passed
            if (deltaY > 150 && isAtTop && (now - lastPullTime) > 2000) {
                lastPullTime = now;
                if (webView != null) {
                    webView.reload();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void injectPullRefreshScript(WebView view) {
        String script = "javascript:(function(){" +
            "var s=0;document.addEventListener('touchstart',function(e){s=e.touches[0].pageY;},false);" +
            "document.addEventListener('touchmove',function(e){" +
            "  var d=e.touches[0].pageY-s;" +
            "  if(d>120&&s<80&&window.scrollY<10){" +
            "    window.location='istar://refresh';" +
            "  }" +
            "},false);" +
            "})();";
        view.evaluateJavascript(script, null);
    }

    private void injectFcmToken(WebView view) {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) return;
                String token = task.getResult().replace("'", "\\'");
                String js = "if(typeof window.__registerFCMToken==='function'){window.__registerFCMToken('" + token + "');}";
                view.post(() -> view.evaluateJavascript(js, null));
            });
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