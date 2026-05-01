package hk.istars.s;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

public class PullableWebView extends WebView {

    private float startY;
    private boolean isAtTop = true;
    private PullRefreshCallback callback;

    public interface PullRefreshCallback {
        void onPullToRefresh();
    }

    public void setPullRefreshCallback(PullRefreshCallback callback) {
        this.callback = callback;
    }

    public void setAtTop(boolean atTop) {
        this.isAtTop = atTop;
    }

    public PullableWebView(Context context) {
        super(context);
    }

    public PullableWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PullableWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float currentY = event.getY();
                float deltaY = currentY - startY;
                // Pulling down from top of page
                if (isAtTop && deltaY > 0 && callback != null) {
                    callback.onPullToRefresh();
                }
                break;
        }
        return super.onTouchEvent(event);
    }
}