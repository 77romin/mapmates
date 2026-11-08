package com.mapmates.companion.service;
import com.mapmates.common.db.*;import com.mapmates.common.exception.ApiException;import com.mapmates.common.web.Validator;import java.sql.*;import java.util.*;
/** Plan row is the shared lock for requests, approvals and capacity changes. */
public final class ApprovalService {
 public record Apply(String introduction){}
 public record Decision(String status){}
 public static Map<String,Object> lock(Connection c,long id)throws SQLException {
  var v=Sql.one(c,"SELECT plan_id FROM companions WHERE id=?",id);if(v==null)throw ApiException.notFound("동행을 찾을 수 없어요.");
  Sql.one(c,"SELECT id FROM plans WHERE id=? FOR UPDATE",v.get("plan_id"));
  var row=Sql.one(c,"SELECT c.*,p.owner_id,p.capacity,p.start_date,p.end_date FROM companions c JOIN plans p ON p.id=c.plan_id WHERE c.id=?",id);
  if(row==null)throw ApiException.notFound("동행을 찾을 수 없어요.");return row;
 }
 public static void host(Map<String,Object> v,long user){if(Sql.number(v,"owner_id")!=user)throw ApiException.forbidden("모집자만 처리할 수 있어요.");}
 public static void available(Connection c,Map<String,Object> v,long id)throws SQLException {
  TripLifecycle.joinable(v);
  long count=Sql.number(Sql.one(c,"SELECT 1+COUNT(*) n FROM companion_participants WHERE companion_id=?",id),"n");
  if(!"open".equals(v.get("status"))||count>=Sql.number(v,"capacity"))throw ApiException.conflict("모집이 마감되었어요.");
 }
 public Object requests(long id,long user){return Tx.read(c->{var v=Sql.one(c,"SELECT p.owner_id FROM companions c JOIN plans p ON p.id=c.plan_id WHERE c.id=?",id);if(v==null)throw ApiException.notFound("동행을 찾을 수 없어요.");host(v,user);return Sql.rows(c,"SELECT CAST(r.member_id AS CHAR) memberId,m.nickname,r.status,r.introduction,r.created_at createdAt FROM companion_requests r JOIN members m ON m.id=r.member_id WHERE r.companion_id=? ORDER BY r.created_at,r.member_id",id);});}
 public Object mine(long id,long user){return Tx.read(c->{var v=Sql.one(c,"SELECT status,introduction FROM companion_requests WHERE companion_id=? AND member_id=?",id,user);return v==null?Map.of("status","none"):v;});}
 public void apply(long id,long user,String introduction){String intro=Validator.optionalText(introduction,500,"자기소개");Tx.write(c->{var v=lock(c,id);if(Sql.number(v,"owner_id")==user)throw ApiException.conflict("모집자는 이미 참가 중이에요.");
  if(Sql.one(c,"SELECT 1 FROM companion_participants WHERE companion_id=? AND member_id=?",id,user)!=null)return null;
  var old=Sql.one(c,"SELECT status FROM companion_requests WHERE companion_id=? AND member_id=?",id,user);
  if(old!=null&&"pending".equals(old.get("status")))return null;
  com.mapmates.moderation.SafetyService.interaction(c,user,Sql.number(v,"owner_id"));
  available(c,v,id);
  boolean instant="instant".equals(v.get("join_policy"));
  if(!instant&&(intro==null||intro.isBlank()))throw ApiException.badRequest("참가 자기소개를 입력해주세요.");
  Sql.update(c,"INSERT INTO companion_requests(companion_id,member_id,status,introduction) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),introduction=VALUES(introduction)",id,user,instant?"approved":"pending",intro==null?"":intro);
  if(instant)Sql.update(c,"INSERT INTO companion_participants(companion_id,member_id) VALUES(?,?)",id,user);
  com.mapmates.notification.Notifications.emit(c,Sql.number(v,"owner_id"),user,instant?"joined":"join_requested",instant?"동행에 새 회원이 참가했어요.":"새로운 동행 참가 신청이 있어요.","companion-detail.html?id="+id);return null;});}
 public void decide(long id,long member,long host,String status){Validator.requireOneOf(status,"처리 결과","approved","rejected");Tx.write(c->{var v=lock(c,id);host(v,host);var r=Sql.one(c,"SELECT status FROM companion_requests WHERE companion_id=? AND member_id=?",id,member);if(r==null)throw ApiException.notFound("신청을 찾을 수 없어요.");if(!"pending".equals(r.get("status")))throw ApiException.conflict("이미 처리한 신청이에요.");
  if(status.equals("approved")){com.mapmates.moderation.SafetyService.interaction(c,host,member);available(c,v,id);Sql.update(c,"INSERT INTO companion_participants(companion_id,member_id) VALUES(?,?)",id,member);}
  Sql.update(c,"UPDATE companion_requests SET status=? WHERE companion_id=? AND member_id=?",status,id,member);com.mapmates.notification.Notifications.emit(c,member,host,"request_decided",status.equals("approved")?"동행 참가 신청이 승인됐어요.":"동행 참가 신청이 거절됐어요.","companion-detail.html?id="+id);return null;});}
 public void cancel(long id,long user){Tx.write(c->{var trip=lock(c,id);if(java.util.Set.of("completed","cancelled").contains(trip.get("lifecycle"))||java.time.LocalDate.parse(trip.get("end_date").toString()).isBefore(java.time.LocalDate.now()))throw ApiException.conflict("종료·취소된 여행의 참가 기록은 유지돼요.");Sql.update(c,"UPDATE companion_requests SET status='cancelled' WHERE companion_id=? AND member_id=?",id,user);Sql.update(c,"DELETE FROM companion_participants WHERE companion_id=? AND member_id=?",id,user);return null;});}
}
