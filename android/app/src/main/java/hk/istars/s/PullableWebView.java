package hk.istars.s;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

public class PullableWebView extends WebView {

    private float startY = 0;
    private boolean isAtTop = true;
    private OnPullToRefreshListener listener;
    private boolean isPulling = false;

    public interface OnPullToRefreshListener {
        void onPullToRefresh();
    }

    public void setOnPullToRefreshListener(OnPullToRefreshListener listener) {
        this.listener = listener;
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
                isPulling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isPulling && isAtTop) {
                    float currentY = event.getY();
                    float deltaY = currentY - startY;
                    // Pulling down more than 100px from top
                    if (deltaY > 100) {
                        isPulling = true;
                        if (listener != null) {
                            listener.onPullToRefresh();
                        }
                    }
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        isAtTop = (t <= 0);
        if (t > 0) {
            isPulling = false;
        }
    }
}