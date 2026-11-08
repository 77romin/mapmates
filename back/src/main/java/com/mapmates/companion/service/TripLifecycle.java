package com.mapmates.companion.service;
import com.mapmates.common.db.*;import com.mapmates.common.exception.ApiException;import com.mapmates.common.web.Validator;import com.mapmates.notification.Notifications;import java.sql.*;import java.time.*;import java.util.*;
public final class TripLifecycle {
 public record Change(String lifecycle){}
 public static String effective(Map<String,Object> p){String state=Objects.toString(p.get("lifecycle"),"scheduled");if(!state.equals("scheduled"))return state;LocalDate now=LocalDate.now();if(LocalDate.parse(p.get("endDate").toString()).isBefore(now))return "completed";if(!LocalDate.parse(p.get("startDate").toString()).isAfter(now))return "ongoing";return state;}
 public static void joinable(Map<String,Object> v){if(!"scheduled".equals(v.get("lifecycle"))||LocalDate.parse(v.get("start_date").toString()).isBefore(LocalDate.now()))throw ApiException.conflict("출발했거나 종료·취소된 여행에는 참가할 수 없어요.");}
 public static void editable(Connection c,long plan)throws SQLException {var p=Sql.one(c,"SELECT c.lifecycle,p.start_date AS startDate,p.end_date AS endDate FROM companions c JOIN plans p ON p.id=c.plan_id WHERE p.id=?",plan);if(p!=null&&Set.of("completed","cancelled").contains(effective(p)))throw ApiException.conflict("종료·취소된 여행은 변경할 수 없어요.");}
 public void change(long id,long user,String next){Validator.requireOneOf(next,"여행 상태","completed","cancelled");Tx.write(c->{var v=ApprovalService.lock(c,id);ApprovalService.host(v,user);var p=Sql.one(c,"SELECT start_date AS startDate,end_date AS endDate FROM plans WHERE id=?",v.get("plan_id"));p.put("lifecycle",v.get("lifecycle"));if(Set.of("completed","cancelled").contains(effective(p)))throw ApiException.conflict("이미 종료·취소된 여행이에요.");if(next.equals("completed")&&LocalDate.parse(p.get("startDate").toString()).isAfter(LocalDate.now()))throw ApiException.conflict("출발 전에는 여행을 종료할 수 없어요.");
  Notifications.participants(c,id,user,"trip_state",next.equals("cancelled")?"참가한 여행이 취소됐어요.":"참가한 여행이 종료됐어요.");
  for(var r:Sql.rows(c,"SELECT member_id FROM companion_requests WHERE companion_id=? AND status='pending'",id))Notifications.emit(c,Sql.number(r,"member_id"),user,"trip_state","신청한 여행이 마감됐어요.","companion-detail.html?id="+id);
  Sql.update(c,"UPDATE companion_requests SET status='cancelled' WHERE companion_id=? AND status='pending'",id);
  Sql.update(c,"UPDATE companions SET lifecycle=?,status='closed' WHERE id=?",next,id);return null;});}
}
