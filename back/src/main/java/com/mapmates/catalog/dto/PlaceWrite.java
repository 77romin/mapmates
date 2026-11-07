package com.mapmates.catalog.dto;

public record PlaceWrite(
    Integer id,
    String title,
    Double lat,
    Double lng,
    String image,
    String address,
    Integer contentTypeId) {}
