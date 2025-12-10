package com.mjc.mascotalink.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.Map;

public class HomeViewModel extends ViewModel {
    private final HomeRepository repository = new HomeRepository();

    private final MediatorLiveData<Map<String, Object>> userProfile = new MediatorLiveData<>();
    private final MediatorLiveData<Map<String, Object>> activeReservation = new MediatorLiveData<>();
    private final MediatorLiveData<Map<String, Object>> walkerStats = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MediatorLiveData<String> error = new MediatorLiveData<>();

    public HomeViewModel() {
        // Observe repository errors and propagate them
        error.addSource(repository.getLastError(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                error.setValue(errorMsg);
            }
        });
    }

    public LiveData<Map<String, Object>> getUserProfile() {
        return userProfile;
    }

    public LiveData<Map<String, Object>> getActiveReservation() {
        return activeReservation;
    }

    public LiveData<Map<String, Object>> getWalkerStats() {
        return walkerStats;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadUserData(String userId) {
        isLoading.setValue(true);
        LiveData<Map<String, Object>> source = repository.getUserProfile(userId);
        userProfile.addSource(source, data -> {
            userProfile.setValue(data);
            isLoading.setValue(false);
        });
    }

    public void loadWalkerStats(String userId) {
        LiveData<Map<String, Object>> source = repository.getWalkerStats(userId);
        walkerStats.addSource(source, walkerStats::setValue);
    }

    public void listenToActiveReservation(String userId, String role) {
        LiveData<Map<String, Object>> source = repository.getActiveReservation(userId, role);
        activeReservation.addSource(source, activeReservation::setValue);
    }

    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    public void setError(String errorMsg) {
        error.setValue(errorMsg);
    }

    public void clearError() {
        error.setValue(null);
    }
}