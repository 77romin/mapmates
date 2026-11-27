package com.ssafy.mapmates.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Member {
    private Integer id;
    private String userId;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String name;
    private String email;
    private String role;
    private String tags;
    private Double homeLat;
    private Double homeLng;
    private String createdAt;
}
