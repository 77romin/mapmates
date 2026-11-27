package com.ssafy.mapmates.dto;

import java.util.List;

import lombok.Data;

@Data
public class Board {
    private Integer id;
    private String type; // NOTICE / FREE
    private Integer memberId;
    private String writerName;
    private String title;
    private String content;
    private Integer hit;
    private String createdAt;
    private Integer commentCount;
    private List<Comment> comments;
}
