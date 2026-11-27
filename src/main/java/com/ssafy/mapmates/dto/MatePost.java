package com.ssafy.mapmates.dto;

import lombok.Data;

@Data
public class MatePost {
    private Integer id;
    private Integer planId;
    private Integer memberId;
    private String writerName;
    private String title;
    private String content;
    private Integer capacity;
    private String tags;
    private String status;
    private String createdAt;
    // 조회용
    private String planTitle;
    private String startDate;
    private String endDate;
    private Integer joinedCount;
    /** 로그인 사용자의 취향 태그와의 일치도(0~100) */
    private Integer matchRate;
    /** 로그인 사용자의 신청 상태 */
    private String myStatus;
}
