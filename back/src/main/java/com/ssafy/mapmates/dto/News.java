package com.ssafy.mapmates.dto;

import lombok.Data;

@Data
public class News {
    private Integer id;
    private String keyword;
    private String title;
    private String link;
    private String source;
    private String pubDate;
    private String createdAt;
}
