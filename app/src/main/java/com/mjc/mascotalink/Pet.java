package com.mjc.mascotalink;

public class Pet {
    private String id;
    private String name;
    private String avatarUrl;

    public Pet() {
        // Constructor vacío requerido para Firestore
    }

    public Pet(String id, String name, String avatarUrl) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
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

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
