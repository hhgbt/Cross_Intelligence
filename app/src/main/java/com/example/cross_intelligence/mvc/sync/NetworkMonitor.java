package com.example.cross_intelligence.mvc.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class NetworkMonitor implements NetworkStatusProvider {

    public interface Listener {
        void onNetworkChanged(boolean connected);
    }

    private final ConnectivityManager connectivityManager;
    private final ConnectivityManager.NetworkCallback networkCallback;
    private final MutableLiveData<Boolean> networkStatus = new MutableLiveData<>(false);

    public NetworkMonitor(@NonNull Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                notifyStatus(true);
            }

            @Override
            public void onLost(@NonNull Network network) {
                notifyStatus(false);
            }
        };
    }

    @MainThread
    public void start() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
        notifyStatus(isCurrentlyConnected());
    }

    @MainThread
    public void stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public boolean isConnected() {
        Boolean value = networkStatus.getValue();
        return value != null && value;
    }

    @Override
    public LiveData<Boolean> getNetworkStatus() {
        return networkStatus;
    }

    private void notifyStatus(boolean connected) {
        networkStatus.postValue(connected);
    }

    private boolean isCurrentlyConnected() {
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // Visible for testing
    void simulateStatus(boolean connected) {
        notifyStatus(connected);
    }
}

