package com.mapmates.notification;
import com.mapmates.common.db.*;import com.mapmates.common.web.*;import com.mapmates.common.exception.ApiException;import jakarta.servlet.annotation.WebServlet;import java.util.*;
@WebServlet("/api/notifications/*")
public class NotificationServlet extends BaseApiServlet {
 protected void routes(Routes r){
  r.get("/",q->{long user=q.requireLogin().id();int page=q.queryInt("page",1,1,100000),size=q.queryInt("size",20,1,100);return Tx.read(c->Map.of("items",Sql.rows(c,"SELECT id,kind,title,link,read_at AS readAt,created_at AS createdAt FROM notifications WHERE member_id=? ORDER BY id DESC LIMIT ? OFFSET ?",user,size,(page-1)*size),"unread",Sql.number(Sql.one(c,"SELECT COUNT(*) n FROM notifications WHERE member_id=? AND read_at IS NULL",user),"n"),"total",Sql.number(Sql.one(c,"SELECT COUNT(*) n FROM notifications WHERE member_id=?",user),"n"),"page",page,"size",size));});
  r.put("/read-all",q->{Tx.write(c->Sql.update(c,"UPDATE notifications SET read_at=COALESCE(read_at,NOW()) WHERE member_id=?",q.requireLogin().id()));return null;});
  r.put("/{id}/read",q->{long user=q.requireLogin().id();Tx.write(c->{if(Sql.one(c,"SELECT id FROM notifications WHERE id=? AND member_id=?",q.pathLong("id"),user)==null)throw ApiException.notFound("알림을 찾을 수 없어요.");return Sql.update(c,"UPDATE notifications SET read_at=COALESCE(read_at,NOW()) WHERE id=? AND member_id=?",q.pathLong("id"),user);});return null;});
 }
}
