package com.ssafy.mapmates.dto;

import lombok.Data;

@Data
public class Comment {
    private Integer id;
    private Integer boardId;
    private Integer memberId;
    private String writerName;
    private String content;
    private String createdAt;
}
