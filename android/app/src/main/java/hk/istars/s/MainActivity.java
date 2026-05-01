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
import android.widget.Toast;
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
        int action = ev.getAction();
        float y = ev.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            touchStartY = y;
            isPulling = false;
            pullProgress = 0;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float deltaY = y - touchStartY;
            if (deltaY > 0) {
                pullProgress = Math.min(deltaY / 150, 1.0f);
                if (pullProgress > 0.05f && webView != null) {
                    webView.evaluateJavascript(
                        "if(window.__setPullProgress){window.__setPullProgress(" + pullProgress + ");}else{console.log('no __setPullProgress');}",
                        null
                    );
                }
                if (deltaY > 150) {
                    long now = System.currentTimeMillis();
                    if (now - lastPullTime > 2000) {
                        isPulling = true;
                        lastPullTime = now;
                        Toast.makeText(this, "正在刷新...", Toast.LENGTH_SHORT).show();
                        if (webView != null) {
                            webView.evaluateJavascript(
                                "if(window.__setRefreshing){window.__setRefreshing(true);}else{console.log('no __setRefreshing');}",
                                null
                            );
                            webView.reload();
                        }
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (!isPulling && webView != null) {
                webView.evaluateJavascript("if(window.__setPullProgress){window.__setPullProgress(0);}", null);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void injectPullRefreshUI(WebView view) {
        String js = "javascript:(function(){" +
            "if(window.__pullReady)return;" +
            "window.__pullReady=true;" +
            "var c=document.createElement('div');" +
            "c.innerHTML='<div style=\"position:fixed;top:0;left:0;right:0;height:56px;display:flex;align-items:center;justify-content:center;z-index:2147483647;pointer-events:none;opacity:0;background:linear-gradient(135deg,#2196F3,#64B5F6);box-shadow:0 2px 8px rgba(0,0,0,0.15);\">" +
            "<div id=\"_ps\" style=\"width:24px;height:24px;border-radius:50%;border:3px solid rgba(255,255,255,0.4);border-top-color:white;transform:scale(0);transition:transform 0.2s;\"></div>" +
            "<span id=\"_pt\" style=\"color:white;margin-left:12px;font-size:14px;font-weight:500;opacity:0;\">釋放以重新整理</span>" +
            "</div>';" +
            "document.body.appendChild(c);" +
            "var s=document.getElementById('_ps');" +
            "var t=document.getElementById('_pt');" +
            "window.__setPullProgress=function(p){" +
            "  if(p<0.08){c.style.opacity='0';s.style.transform='scale(0)';t.style.opacity='0';}" +
            "  else{c.style.opacity='1';s.style.transform='scale('+Math.min(p,1)+')';t.style.opacity=(p>0.4)?'1':'0';if(p>=1){s.style.transform='scale(1)';s.style.borderTopColor='transparent';s.style.borderRightColor='white';s.style.animation='_spin 0.6s linear infinite';}}" +
            "};" +
            "window.__setRefreshing=function(v){" +
            "  if(v){c.style.opacity='1';s.style.transform='scale(1)';s.style.borderTopColor='transparent';s.style.borderRightColor='white';s.style.animation='_spin 0.6s linear infinite';t.style.opacity='1';t.textContent='正在重新整理...';}" +
            "  else{c.style.opacity='0';s.style.transform='scale(0)';t.textContent='釋放以重新整理';setTimeout(function(){s.style.animation='';s.style.borderTopColor='white';s.style.borderRightColor='transparent';t.style.opacity='0';},300);}" +
            "};" +
            "var st=document.createElement('style');" +
            "st.textContent='@keyframes _spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}';" +
            "document.head.appendChild(st);" +
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