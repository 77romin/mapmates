package com.ssafy.mapmates.dto;

import lombok.Data;

@Data
public class MateApplication {
    private Integer id;
    private Integer postId;
    private Integer memberId;
    private String memberName;
    private String memberTags;
    private String message;
    private String status;
    private String createdAt;
}
