package com.mjc.mascotalink;

public class Pet {
    private String id;
    private String name;
    private String breed;
    private String avatarUrl;
    private String ownerId; // ID del dueño

    public Pet() {
        // Constructor vacío requerido para Firestore
    }

    public Pet(String id, String name, String breed, String avatarUrl, String ownerId) {
        this.id = id;
        this.name = name;
        this.breed = breed;
        this.avatarUrl = avatarUrl;
        this.ownerId = ownerId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
}
