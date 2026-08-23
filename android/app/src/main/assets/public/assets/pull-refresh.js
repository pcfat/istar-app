// Pull-to-refresh indicator
(function() {
    var container = null;
    var spinner = null;
    var refreshing = false;

    function init() {
        container = document.createElement('div');
        container.innerHTML = '<div style="position:fixed;top:0;left:0;right:0;height:56px;display:flex;align-items:center;justify-content:center;z-index:9999999;pointer-events:none;opacity:0;background:linear-gradient(135deg,#2196F3,#64B5F6);box-shadow:0 2px 8px rgba(0,0,0,0.15);font-family:-apple-system,BlinkMacSystemFont,sans-serif;">' +
            '<div id="__pull_spinner" style="width:24px;height:24px;border-radius:50%;border:3px solid rgba(255,255,255,0.4);border-top-color:white;transform:scale(0);transition:transform 0.2s;"></div>' +
            '<span style="color:white;margin-left:12px;font-size:14px;font-weight:500;opacity:0;">釋放以重新整理</span>' +
            '</div>';
        document.body.appendChild(container);
        spinner = document.getElementById('__pull_spinner');
    }

    init();

    window.__setPullProgress = function(p) {
        if (refreshing) return;
        var indicator = container.querySelector('span');
        if (p < 0.08) {
            container.style.opacity = '0';
            spinner.style.transform = 'scale(0)';
            indicator.style.opacity = '0';
        } else {
            container.style.opacity = '1';
            spinner.style.transform = 'scale(' + Math.min(p, 1) + ')';
            indicator.style.opacity = (p > 0.3) ? '1' : '0';
        }
        if (p >= 1) {
            spinner.style.transform = 'scale(1)';
            spinner.style.borderTopColor = 'transparent';
            spinner.style.borderRightColor = 'white';
            spinner.style.animation = '__spinset 0.6s linear infinite';
        }
    };

    window.__setRefreshing = function(v) {
        refreshing = v;
        var indicator = container.querySelector('span');
        if (v) {
            container.style.opacity = '1';
            spinner.style.transform = 'scale(1)';
            spinner.style.borderTopColor = 'transparent';
            spinner.style.borderRightColor = 'white';
            spinner.style.animation = '__spinset 0.6s linear infinite';
            indicator.style.opacity = '1';
            indicator.textContent = '正在重新整理...';
        } else {
            container.style.opacity = '0';
            spinner.style.transform = 'scale(0)';
            indicator.textContent = '釋放以重新整理';
            setTimeout(function() {
                spinner.style.animation = '';
                spinner.style.borderTopColor = 'white';
                spinner.style.borderRightColor = 'transparent';
                indicator.style.opacity = '0';
            }, 300);
        }
    };

    var style = document.createElement('style');
    style.textContent = '@keyframes __spinset{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}';
    document.head.appendChild(style);
})();