import UIKit
import Capacitor
import WebKit

class CustomViewController: CAPBridgeViewController {
    
    private var pullRefreshIndicator: UIView?
    private var activityIndicator: UIActivityIndicatorView?
    private var touchStartY: CGFloat = 0
    private var isPulling = false
    private let pullThreshold: CGFloat = 150
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupPullToRefresh()
    }
    
    private func setupPullToRefresh() {
        // Create container with white circular background
        let container = UIView(frame: CGRect(x: 0, y: 0, width: 70, height: 70))
        container.center = CGPoint(x: view.bounds.width / 2, y: 50)
        container.backgroundColor = .white
        container.layer.cornerRadius = 35
        container.layer.shadowColor = UIColor.black.cgColor
        container.layer.shadowOpacity = 0.1
        container.layer.shadowOffset = CGSize(width: 0, height: 2)
        container.layer.shadowRadius = 8
        container.layer.borderWidth = 0.5
        container.layer.borderColor = UIColor(white: 0.88, alpha: 1.0).cgColor
        container.alpha = 0
        container.transform = CGAffineTransform(scaleX: 0.7, y: 0.7)
        
        // Create activity indicator
        let indicator: UIActivityIndicatorView
        if #available(iOS 13.0, *) {
            indicator = UIActivityIndicatorView(style: .medium)
        } else {
            indicator = UIActivityIndicatorView(style: .gray)
        }
        indicator.color = UIColor(red: 0.1, green: 0.67, blue: 0.88, alpha: 1.0) // #1AABE0
        indicator.frame = container.bounds
        indicator.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        indicator.startAnimating()
        
        container.addSubview(indicator)
        view.addSubview(container)
        
        pullRefreshIndicator = container
        activityIndicator = indicator
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        if let touch = touches.first {
            touchStartY = touch.location(in: view).y
            isPulling = false
        }
    }
    
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesMoved(touches, with: event)
        
        guard let touch = touches.first,
              let webView = webView,
              let scrollView = webView.scrollView else {
            return
        }
        
        let currentY = touch.location(in: view).y
        let deltaY = currentY - touchStartY
        
        // Only trigger when at top of scroll
        if scrollView.contentOffset.y <= 0 && deltaY > 0 {
            let progress = min(deltaY / pullThreshold, 1.0)
            
            if let indicator = pullRefreshIndicator {
                indicator.alpha = progress * 0.9
                indicator.center.y = 50 + deltaY * 0.4
                let scale = 0.7 + progress * 0.3
                indicator.transform = CGAffineTransform(scaleX: scale, y: scale)
            }
            
            if deltaY > pullThreshold && !isPulling {
                isPulling = true
            }
        }
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        handleTouchEnd()
    }
    
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesCancelled(touches, with: event)
        handleTouchEnd()
    }
    
    private func handleTouchEnd() {
        guard let webView = webView,
              let scrollView = webView.scrollView,
              let indicator = pullRefreshIndicator else {
            return
        }
        
        if isPulling && scrollView.contentOffset.y <= 0 {
            // Trigger refresh
            UIView.animate(withDuration: 0.2) {
                indicator.center.y = 50
                indicator.transform = CGAffineTransform(scaleX: 1.0, y: 1.0)
                indicator.alpha = 1.0
            }
            
            webView.reload()
            
            // Hide indicator after delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                UIView.animate(withDuration: 0.3) {
                    indicator.alpha = 0
                    indicator.center.y = 50
                }
            }
        } else {
            // Cancel pull
            UIView.animate(withDuration: 0.2) {
                indicator.alpha = 0
                indicator.center.y = 50
                indicator.transform = CGAffineTransform(scaleX: 0.7, y: 0.7)
            }
        }
        
        isPulling = false
    }
}
