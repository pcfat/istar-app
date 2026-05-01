package hk.istars.s;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import android.util.Log;
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
    private static final String TAG = "MainActivity";
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private WebView webView;
    private float touchStartY = 0;
    private long lastPullTime = 0;
    private boolean isPulling = false;
    private float pullProgress = 0;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

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
        Log.d(TAG, "webView=" + webView);

        if (webView != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            Log.d(TAG, "JavaScript enabled");

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    Log.d(TAG, "onReceivedError");
                    if (request.isForMainFrame()) {
                        view.loadUrl("file:///android_asset/public/error.html");
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return false;
                }

                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    loadPullRefreshScript(view);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
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
        int action = ev.getAction();
        float y = ev.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            touchStartY = y;
            isPulling = false;
            pullProgress = 0;
            Log.d(TAG, "TOUCH DOWN touchStartY=" + touchStartY);
        } else if (action == MotionEvent.ACTION_MOVE) {
            float deltaY = y - touchStartY;
            if (deltaY > 0) {
                pullProgress = Math.min(deltaY / 150, 1.0f);
                if (pullProgress > 0.05f && webView != null) {
                    webView.evaluateJavascript("if(window.__setPullProgress){window.__setPullProgress(" + pullProgress + ");}else{console.log('NOT FOUND');}", null);
                    if (pullProgress > 0.3f) {
                        Toast.makeText(this, "下拉進度: " + (int)(pullProgress * 100) + "%", Toast.LENGTH_SHORT).show();
                    }
                }
                if (deltaY > 150) {
                    long now = System.currentTimeMillis();
                    if (now - lastPullTime > 2000) {
                        isPulling = true;
                        lastPullTime = now;
                        Toast.makeText(this, "正在刷新...", Toast.LENGTH_SHORT).show();
                        if (webView != null) {
                            webView.evaluateJavascript("if(window.__setRefreshing){window.__setRefreshing(true);}else{console.log('NOT FOUND');}", null);
                            webView.reload();
                        }
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            Log.d(TAG, "TOUCH UP isPulling=" + isPulling);
            if (!isPulling && webView != null) {
                webView.evaluateJavascript("if(window.__setPullProgress){window.__setPullProgress(0);}", null);
            }
        }

        return super.dispatchTouchEvent(ev);
    }

    private void loadPullRefreshScript(WebView view) {
        Toast.makeText(this, "加載刷新組件...", Toast.LENGTH_SHORT).show();
        String js = "(function(){" +
            "var s=document.createElement('script');" +
            "s.src='file:///android_asset/public/assets/pull-refresh.js';" +
            "s.onload=function(){console.log('loaded');window.__setPullProgress&&window.__setPullProgress(0);};" +
            "s.onerror=function(){console.error('failed');};" +
            "document.head.appendChild(s);" +
            "})();";
        view.evaluateJavascript(js, null);
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