// Push Notification initialization for Capacitor
(function() {
    console.log('Push notification initialization started');
    
    // Wait for Capacitor to be ready
    document.addEventListener('deviceready', initializePushNotifications, false);
    
    // Fallback: also try after a short delay if deviceready doesn't fire
    setTimeout(initializePushNotifications, 1000);
    
    function initializePushNotifications() {
        // Check if Capacitor and PushNotifications are available
        if (typeof Capacitor === 'undefined' || !Capacitor.Plugins || !Capacitor.Plugins.PushNotifications) {
            console.log('PushNotifications plugin not available');
            return;
        }
        
        const PushNotifications = Capacitor.Plugins.PushNotifications;
        
        console.log('Initializing Push Notifications...');
        
        // Request permission to use push notifications
        PushNotifications.requestPermissions().then(result => {
            if (result.receive === 'granted') {
                // Register with Apple / Google to receive push via APNS/FCM
                PushNotifications.register();
                console.log('Push notification registration requested');
            } else {
                console.log('Push notification permission denied');
            }
        });
        
        // On success, we should be able to receive notifications
        PushNotifications.addListener('registration', (token) => {
            console.log('Push registration success, token: ' + token.value);
            
            // Store token locally
            localStorage.setItem('pushToken', token.value);
            
            // Send token to your server
            sendTokenToServer(token.value);
        });
        
        // Some issue with your setup and push will not work
        PushNotifications.addListener('registrationError', (error) => {
            console.error('Error on registration: ' + JSON.stringify(error));
        });
        
        // Show us the notification payload if the app is open on our device
        PushNotifications.addListener('pushNotificationReceived', (notification) => {
            console.log('Push notification received: ' + JSON.stringify(notification));
            
            // You can display a custom notification UI here if needed
            if (notification.title || notification.body) {
                alert(notification.title + '\n' + notification.body);
            }
        });
        
        // Method called when tapping on a notification
        PushNotifications.addListener('pushNotificationActionPerformed', (notification) => {
            console.log('Push notification action performed: ' + JSON.stringify(notification));
            
            // Handle notification tap - navigate to specific page if needed
            const data = notification.notification.data;
            if (data && data.url) {
                window.location.href = data.url;
            }
        });
    }
    
    function sendTokenToServer(token) {
        // Send the token to your backend server
        fetch('/api/register-push-token', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                token: token,
                platform: Capacitor.getPlatform() // 'ios' or 'android'
            })
        })
        .then(response => response.json())
        .then(data => {
            console.log('Token sent to server:', data);
        })
        .catch(error => {
            console.error('Error sending token to server:', error);
        });
    }
})();
