package com.ssafy.mapmates.dto;

import lombok.Data;

@Data
public class Hotplace {
    private Integer id;
    private Integer memberId;
    private String writerName;
    private String title;
    private String description;
    private String placeType;
    private String visitedDate;
    private Double latitude;
    private Double longitude;
    private String imagePath;
    private String createdAt;
}
