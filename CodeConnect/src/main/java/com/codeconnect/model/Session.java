package com.codeconnect.model;

/**
 * Thread-safe global session context for CodeConnect.
 * Holds the currently logged-in user in a volatile static reference to ensure
 * visibility across multiple threads, such as background tasks initializing
 * the database and the primary JavaFX Application thread reading session data.
 */
public class Session {
    private static volatile User currentUser;

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }
}
