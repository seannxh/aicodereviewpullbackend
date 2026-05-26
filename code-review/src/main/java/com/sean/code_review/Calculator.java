package com.sean.code_review;

public class Calculator {
    public int divide(int a, int b) {
        return a / b;
    }

    public String getUserData(String userId) {
        String query = "SELECT * FROM users WHERE id = " + userId;
        return query;
    }
}