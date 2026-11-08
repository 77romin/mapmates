package com.mapmates.travel.dto;
public record CompanionWrite(Long planId,String title,String description,Integer capacity,Boolean closed,Long expectedVersion,String joinPolicy) {
 public CompanionWrite(Long planId,String title,String description,Integer capacity,Boolean closed){this(planId,title,description,capacity,closed,null,null);}
}
