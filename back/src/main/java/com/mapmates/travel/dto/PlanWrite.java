package com.mapmates.travel.dto;

import com.mapmates.catalog.dto.PlaceWrite;
import java.time.*;
import java.util.*;

public record PlanWrite(
    String title,
    LocalDate startDate,
    LocalDate endDate,
    Integer capacity,
    Integer budget,
    String region,
    String theme,
    String description,
    List<Stop> items, Long version) {
  public PlanWrite(String title,LocalDate startDate,LocalDate endDate,Integer capacity,Integer budget,String region,String theme,String description,List<Stop> items){this(title,startDate,endDate,capacity,budget,region,theme,description,items,null);}
  public record Stop(Integer day, String time, String memo, PlaceWrite place) {}
}
