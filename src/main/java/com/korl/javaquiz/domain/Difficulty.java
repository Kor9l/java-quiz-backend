package com.korl.javaquiz.domain;

public enum Difficulty {
    EASY,
    MEDIUM,
    HARD;

    public double weight() {
        return switch (this) {
            case EASY -> 1.0;
            case MEDIUM -> 1.15;
            case HARD -> 1.3;
        };
    }
}
