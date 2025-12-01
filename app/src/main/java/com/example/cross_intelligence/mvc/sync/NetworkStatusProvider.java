package com.example.cross_intelligence.mvc.sync;

import androidx.lifecycle.LiveData;

public interface NetworkStatusProvider {
    boolean isConnected();

    LiveData<Boolean> getNetworkStatus();
}





