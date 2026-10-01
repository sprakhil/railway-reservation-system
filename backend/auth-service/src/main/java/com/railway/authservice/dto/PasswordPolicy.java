package com.railway.authservice.dto;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static final String REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,30}$";
    public static final String MESSAGE = "Password must be 8-30 characters with uppercase, lowercase, a number and a special character (@#$%^&+=!).";
    public static void validate(String password) {
        if (password == null || !password.matches(REGEX)) {
            throw new com.railway.authservice.exception.CustomAuthException(MESSAGE);
        }
    }
}
