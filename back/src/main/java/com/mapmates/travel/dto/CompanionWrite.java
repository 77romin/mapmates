package com.mapmates.travel.dto;

public record CompanionWrite(
    Long planId, String title, String description, Integer capacity, Boolean closed) {}
