package com.mjc.mascotalink.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.Map;

public class HomeViewModel extends ViewModel {
    private final HomeRepository repository = new HomeRepository();
    
    private final MutableLiveData<Map<String, Object>> userProfile = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Object>> activeReservation = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Object>> walkerStats = new MutableLiveData<>();

    public LiveData<Map<String, Object>> getUserProfile() {
        return userProfile;
    }

    public LiveData<Map<String, Object>> getActiveReservation() {
        return activeReservation;
    }
    
    public LiveData<Map<String, Object>> getWalkerStats() {
        return walkerStats;
    }

    public void loadUserData(String userId) {
        repository.getUserProfile(userId).observeForever(userProfile::setValue);
    }
    
    public void loadWalkerStats(String userId) {
        repository.getWalkerStats(userId).observeForever(walkerStats::setValue);
    }

    public void listenToActiveReservation(String userId, String role) {
        repository.getActiveReservation(userId, role).observeForever(activeReservation::setValue);
    }
}