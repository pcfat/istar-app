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
    private long lastPullTime = 0;
    private boolean isPulling = false;
    private Handler handler = new Handler();

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
                    if (request.isForMainFrame()) {
                        view.loadUrl("file:///android_asset/public/error.html");
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    injectPullRefreshUI(view);
                    injectFcmToken(view);
                }
            });
        }

        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) return;
                final String token = task.getResult().replace("'", "\\'");
                Handler h = new Handler(getMainLooper());
                Runnable inject = new Runnable() {
                    int attempts = 0;
                    @Override
                    public void run() {
                        attempts++;
                        if (webView != null) {
                            String js = "if(typeof window.__registerFCMToken==='function'){window.__registerFCMToken('" + token + "');}";
                            webView.evaluateJavascript(js, result -> {
                                if ((result == null || result.equals("null") || result.equals("undefined")) && attempts < 15) {
                                    h.postDelayed(this, 2000);
                                }
                            });
                        }
                    }
                };
                h.postDelayed(inject, 5000);
            });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            touchStartY = ev.getY();
            isPulling = false;
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            float deltaY = ev.getY() - touchStartY;
            if (deltaY > 50 && !isPulling) {
                // Show pull indicator
                if (webView != null) {
                    webView.evaluateJavascript("if(window.__showPullIndicator)window.__showPullIndicator();", null);
                }
            }
            if (deltaY > 150) {
                long now = System.currentTimeMillis();
                if (now - lastPullTime > 2000) {
                    isPulling = true;
                    lastPullTime = now;
                    // Trigger reload
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.__hidePullIndicator)window.__hidePullIndicator();", null);
                        webView.reload();
                    }
                }
            }
        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
            // Hide indicator
            handler.postDelayed(() -> {
                if (webView != null) {
                    webView.evaluateJavascript("if(window.__hidePullIndicator)window.__hidePullIndicator();", null);
                }
            }, 500);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void injectPullRefreshUI(WebView view) {
        String css = "javascript:(function(){" +
            "var indicator=null,spinner=null,isShowing=false;" +
            "window.__showPullIndicator=function(){" +
            "  if(isShowing)return;isShowing=true;" +
            "  indicator=document.createElement('div');" +
            "  indicator.id='__pull_indicator__';" +
            "  indicator.innerHTML='<div style=\"position:fixed;top:0;left:0;right:0;z-index:9999999;background:linear-gradient(135deg,#2196F3,#6EC6FF);color:white;text-align:center;padding:12px;font-family:-apple-system,sans-serif;font-size:14px;display:flex;align-items:center;justify-content:center;gap:8px;box-shadow:0 2px 8px rgba(0,0,0,0.15);\">" +
            "    <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" style=\"animation:__spin 1s linear infinite\"><path fill=\"white\" d=\"M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z\"/></svg>" +
            "    <span>釋放以重新整理...</span>" +
            "  </div>';" +
            "  var style=document.createElement('style');" +
            "  style.textContent='@keyframes __spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}';" +
            "  document.head.appendChild(style);" +
            "  document.body.appendChild(indicator);" +
            "};" +
            "window.__hidePullIndicator=function(){" +
            "  if(indicator){indicator.remove();indicator=null;}" +
            "  isShowing=false;" +
            "};" +
            "})();";
        view.evaluateJavascript(css, null);
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