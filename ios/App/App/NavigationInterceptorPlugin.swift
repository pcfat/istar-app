import Foundation
import Capacitor
import WebKit

@objc(NavigationInterceptorPlugin)
public class NavigationInterceptorPlugin: CAPPlugin {
    
    override public func load() {
        // Hook into the bridge's webView
        if let bridge = self.bridge,
           let webView = bridge.webView {
            webView.navigationDelegate = self
        }
    }
}

extension NavigationInterceptorPlugin: WKNavigationDelegate {
    
    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        
        guard let url = navigationAction.request.url else {
            decisionHandler(.allow)
            return
        }
        
        // Allow all istars.hk domain navigation within the same WebView
        if let host = url.host, host.contains("istars.hk") {
            // Force load in the same WebView, never open external browser
            decisionHandler(.allow)
            return
        }
        
        // For any other domains, also keep in WebView
        decisionHandler(.allow)
    }
    
    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, preferences: WKWebpagePreferences, decisionHandler: @escaping (WKNavigationActionPolicy, WKWebpagePreferences) -> Void) {
        
        guard let url = navigationAction.request.url else {
            decisionHandler(.allow, preferences)
            return
        }
        
        // Force all navigation to stay in WebView
        decisionHandler(.allow, preferences)
    }
    
    public func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        
        // Intercept window.open() and target="_blank" - load in same WebView
        if let url = navigationAction.request.url {
            webView.load(URLRequest(url: url))
        }
        
        // Return nil to prevent new window
        return nil
    }
}
