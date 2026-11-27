package com.ssafy.mapmates.dto;

import java.util.List;

import lombok.Data;

@Data
public class Plan {
    private Integer id;
    private Integer memberId;
    private String ownerName;
    private String title;
    private String startDate;
    private String endDate;
    private Integer budget;
    private String memo;
    private Boolean isPublic;
    private String createdAt;
    private Integer itemCount;
    private List<PlanItem> items;
    private List<Member> members;
}
