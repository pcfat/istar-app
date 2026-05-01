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
    private float pullProgress = 0;
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
            pullProgress = 0;
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            float deltaY = ev.getY() - touchStartY;
            if (deltaY > 0) {
                pullProgress = Math.min(deltaY / 150, 1.0f);
                if (pullProgress > 0.05f) {
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.__setPullProgress)window.__setPullProgress(" + pullProgress + ");", null);
                    }
                }
                if (deltaY > 150) {
                    long now = System.currentTimeMillis();
                    if (now - lastPullTime > 2000) {
                        isPulling = true;
                        lastPullTime = now;
                        pullProgress = 1.0f;
                        if (webView != null) {
                            webView.evaluateJavascript("if(window.__setRefreshing)window.__setRefreshing(true);", null);
                            webView.reload();
                        }
                    }
                }
            }
        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
            if (!isPulling && pullProgress < 0.5f) {
                if (webView != null) {
                    webView.evaluateJavascript("if(window.__setPullProgress)window.__setPullProgress(0);", null);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void injectPullRefreshUI(WebView view) {
        String js = "javascript:(function(){" +
            "var container=null,spinnerEl=null,progressEl=null,refreshing=false;" +
            "function createUI(){" +
            "  container=document.createElement('div');" +
            "  container.id='__pull_container__';" +
            "  container.innerHTML='<div style=\"position:fixed;top:0;left:0;right:0;height:60px;display:flex;align-items:center;justify-content:center;z-index:9999999;pointer-events:none;opacity:0;transition:opacity 0.2s;\">' +
            "    <div id=\"__pull_circle__\" style=\"width:28px;height:28px;border-radius:50%;border:3px solid rgba(33,150,243,0.3);border-top-color:#2196F3;transform:scale(0);\"></div>" +
            "  </div>'; " +
            "  document.body.appendChild(container);" +
            "  spinnerEl=document.getElementById('__pull_circle__');" +
            "}" +
            "createUI();" +
            "window.__setPullProgress=function(p){" +
            "  if(refreshing)return;" +
            "  if(p<0.05){container.style.opacity='0';spinnerEl.style.transform='scale(0)';return;}" +
            "  container.style.opacity='1';" +
            "  spinnerEl.style.transform='scale('+p+')';" +
            "  if(p>=1){spinnerEl.style.transform='scale(1)';" +
            "    spinnerEl.style.background='rgba(33,150,243,0.2)';" +
            "    spinnerEl.innerHTML='<div style=\"width:100%;height:100%;border-radius:50%;border:3px solid transparent;border-top-color:#2196F3;animation:__spin 0.8s linear infinite;box-sizing:border-box;\"></div>';" +
            "  }" +
            "};" +
            "window.__setRefreshing=function(r){" +
            "  refreshing=r;" +
            "  if(r){" +
            "    container.style.opacity='1';" +
            "    spinnerEl.style.transform='scale(1.2)';" +
            "    spinnerEl.style.background='rgba(33,150,243,0.3)';" +
            "    spinnerEl.innerHTML='<div style=\"width:100%;height:100%;border-radius:50%;border:3px solid transparent;border-top-color:#2196F3;border-right-color:#2196F3;animation:__spin 0.6s linear infinite;box-sizing:border-box;\"></div>';" +
            "  } else {" +
            "    container.style.opacity='0';" +
            "    spinnerEl.style.transform='scale(0)';" +
            "    setTimeout(function(){spinnerEl.innerHTML='';spinnerEl.style.background='';},300);" +
            "  }" +
            "};" +
            "var style=document.createElement('style');" +
            "style.textContent='@keyframes __spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}';" +
            "document.head.appendChild(style);" +
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