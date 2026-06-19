package com.sean.code_review.util.test;

public class Calculator {
    public int divide(int a, int b) {
        return a / b;
    }

    public String getUserData(String userId) {
        String query = "SELECT * FROM users WHERE id = " + userId;
        return query;
    }

    public void processData(String data) {
        if (data != null) {
            System.out.println(data);
        }
    }
}