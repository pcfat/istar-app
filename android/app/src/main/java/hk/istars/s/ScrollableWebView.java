package hk.istars.s;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ScrollableWebView extends WebView {

    private SwipeRefreshLayout swipeRefreshLayout;

    public ScrollableWebView(Context context) {
        super(context);
    }

    public ScrollableWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollableWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSwipeRefreshLayout(SwipeRefreshLayout layout) {
        this.swipeRefreshLayout = layout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setEnabled(scrollY == 0);
                }
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.requestDisallowInterceptTouchEvent(false);
        }
        return super.onTouchEvent(event);
    }
}