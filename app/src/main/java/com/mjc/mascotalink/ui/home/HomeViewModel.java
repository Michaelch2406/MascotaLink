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

    private LiveData<Map<String, Object>> userProfileSource;
    private LiveData<Map<String, Object>> activeReservationSource;
    private LiveData<Map<String, Object>> walkerStatsSource;

    public HomeViewModel() {
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
        if (userId == null) return;

        isLoading.setValue(true);

        if (userProfileSource != null) {
            userProfile.removeSource(userProfileSource);
        }

        userProfileSource = repository.getUserProfile(userId);
        userProfile.addSource(userProfileSource, data -> {
            userProfile.setValue(data);
            isLoading.setValue(false);
        });
    }

    public void loadWalkerStats(String userId) {
        if (userId == null) return;

        if (walkerStatsSource != null) {
            walkerStats.removeSource(walkerStatsSource);
        }

        walkerStatsSource = repository.getWalkerStats(userId);
        walkerStats.addSource(walkerStatsSource, walkerStats::setValue);
    }

    public void listenToActiveReservation(String userId, String role) {
        if (userId == null || role == null) return;

        if (activeReservationSource != null) {
            activeReservation.removeSource(activeReservationSource);
        }

        activeReservationSource = repository.getActiveReservation(userId, role);
        activeReservation.addSource(activeReservationSource, activeReservation::setValue);
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

    public com.google.firebase.firestore.FirebaseFirestore getDb() {
        return repository.getDb();
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        if (userProfileSource != null) {
            userProfile.removeSource(userProfileSource);
        }
        if (activeReservationSource != null) {
            activeReservation.removeSource(activeReservationSource);
        }
        if (walkerStatsSource != null) {
            walkerStats.removeSource(walkerStatsSource);
        }

        repository.cleanup();
    }
}
