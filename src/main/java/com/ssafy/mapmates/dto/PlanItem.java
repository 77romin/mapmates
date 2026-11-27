package com.ssafy.mapmates.dto;

import lombok.Data;

@Data
public class PlanItem {
    private Integer id;
    private Integer planId;
    private Integer dayNo;
    private Integer seq;
    private Integer attractionNo;
    private String memo;
    private Integer cost;
    // 조회용(관광지 정보)
    private String title;
    private String addr1;
    private String firstImage1;
    private Double latitude;
    private Double longitude;
    private String contentTypeName;
}
