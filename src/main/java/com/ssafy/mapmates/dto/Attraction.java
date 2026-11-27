package com.ssafy.mapmates.dto;

import lombok.Data;

@Data
public class Attraction {
    private Integer no;
    private Integer contentId;
    private String title;
    private Integer contentTypeId;
    private String contentTypeName;
    private Integer areaCode;
    private Integer siGunGuCode;
    private String sidoName;
    private String gugunName;
    private String firstImage1;
    private Double latitude;
    private Double longitude;
    private String tel;
    private String addr1;
    private String addr2;
    private String homepage;
    private String overview;
    /** 기준점으로부터의 거리(km) - 주변 검색 시에만 채워진다 */
    private Double distance;
}
