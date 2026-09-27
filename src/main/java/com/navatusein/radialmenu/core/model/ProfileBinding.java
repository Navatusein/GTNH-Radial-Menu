package com.navatusein.radialmenu.core.model;

import com.google.gson.annotations.SerializedName;

/** Rule that activates a profile automatically when the player joins a matching world or server. */
public class ProfileBinding {

    public enum Type {

        /** Matches the address the client is connected to, for example gtnh.example.com. */
        @SerializedName("server")
        SERVER,

        /** Matches the single-player world's folder name. */
        @SerializedName("world")
        WORLD,

        /** Matches single player in general, regardless of which world. */
        @SerializedName("singleplayer")
        SINGLEPLAYER
    }

    public Type type;

    public String value;

    public static ProfileBinding server(String address) {
        ProfileBinding binding = new ProfileBinding();
        binding.type = Type.SERVER;
        binding.value = address;
        return binding;
    }

    public static ProfileBinding world(String worldName) {
        ProfileBinding binding = new ProfileBinding();
        binding.type = Type.WORLD;
        binding.value = worldName;
        return binding;
    }

    /**
     * @param serverAddress address of the server the client is on, or null in single player
     * @param worldName     single-player world folder name, or null on a server
     */
    public boolean matches(String serverAddress, String worldName) {
        if (type == null) {
            return false;
        }
        switch (type) {
            case SERVER:
                return serverAddress != null && serverAddress.equalsIgnoreCase(trimmedValue());
            case WORLD:
                return worldName != null && worldName.equalsIgnoreCase(trimmedValue());
            case SINGLEPLAYER:
                return serverAddress == null;
            default:
                return false;
        }
    }

    private String trimmedValue() {
        return value == null ? "" : value.trim();
    }
}
