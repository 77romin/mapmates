package com.mapmates.notification;
import com.mapmates.common.db.*;import java.sql.*;
/** Emitted in the business transaction so rollback never leaves a phantom notification. */
public final class Notifications {
 private Notifications(){}
 public static void emit(Connection c,long recipient,long actor,String kind,String title,String link)throws SQLException {
  if(recipient==actor)return;
  Sql.update(c,"INSERT INTO notifications(member_id,kind,title,link) VALUES(?,?,?,?)",recipient,kind,title,link);
 }
 public static void participants(Connection c,long companion,long actor,String kind,String title)throws SQLException {
  for(var r:Sql.rows(c,"SELECT member_id FROM companion_participants WHERE companion_id=?",companion))emit(c,Sql.number(r,"member_id"),actor,kind,title,"companion-detail.html?id="+companion);
 }
}
