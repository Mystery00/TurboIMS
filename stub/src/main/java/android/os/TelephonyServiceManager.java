package android.os;

public final class TelephonyServiceManager {
    public static final class ServiceRegisterer {
        public IBinder get() {
            return null;
        }
    }

    public ServiceRegisterer getTelephonyServiceRegisterer() {
        return null;
    }

    public ServiceRegisterer getSubscriptionServiceRegisterer() {
        return null;
    }
}
