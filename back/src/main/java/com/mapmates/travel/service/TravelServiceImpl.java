package com.mapmates.travel.service;

import com.mapmates.catalog.dao.*;
import com.mapmates.catalog.dto.PlaceWrite;
import com.mapmates.common.db.*;
import com.mapmates.common.exception.ApiException;
import com.mapmates.common.web.*;
import com.mapmates.travel.dao.*;
import com.mapmates.travel.dto.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class TravelServiceImpl implements TravelService {
  private final TravelDao dao = new TravelDaoImpl();

  private Map<String, Object> required(Map<String, Object> v) {
    if (v == null) throw ApiException.notFound("여행을 찾을 수 없어요.");
    return v;
  }

  private void owner(Map<String, Object> p, long user) {
    if (Sql.number(p, "ownerId") != user)
      throw new ApiException(403, "FORBIDDEN", "작성자만 변경할 수 있어요.");
  }

  private Map<String, Object> decorate(Connection c, Map<String, Object> p) throws SQLException {
    long id = Sql.number(p, "id");
    var items = dao.items(c, id);
    var places = new LinkedHashMap<String, Object>();
    var schedule = new ArrayList<Map<String, Object>>();
    for (var item : items) {
      String key = String.valueOf(item.get("placeId"));
      var place = new LinkedHashMap<>(item);
      place.put("id", item.get("placeId"));
      places.put(key, place);
      schedule.add(item);
    }
    var trip = new LinkedHashMap<>(p);
    trip.put("id", String.valueOf(id));
    trip.put("people", p.get("capacity"));
    return new LinkedHashMap<>(
        Map.of(
            "id",
            String.valueOf(id),
            "ownerId",
            String.valueOf(p.get("ownerId")),
            "trip",
            trip,
            "tripSchedule",
            schedule,
            "itinerary",
            items.stream().map(v -> v.get("placeId")).toList(),
            "itineraryPlaces",
            places));
  }

  public Object plans(long user) {
    return Tx.read(
        c -> {
          var out = new ArrayList<Object>();
          for (var p : dao.plans(c, user)) out.add(decorate(c, p));
          return out;
        });
  }

  public Object plan(long id, long user) {
    return Tx.read(
        c -> {
          var p = required(dao.plan(c, id, false));
          if (Sql.number(p, "ownerId") != user
              && Sql.one(
                      c,
                      "SELECT 1 FROM companions c JOIN companion_participants cp ON"
                          + " cp.companion_id=c.id WHERE c.plan_id=? AND cp.member_id=?",
                      id,
                      user)
                  == null) throw new ApiException(403, "FORBIDDEN", "열람 권한이 없어요.");
          return decorate(c, p);
        });
  }

  public static void validatePlace(PlaceWrite p) {
    if (p == null
        || p.id() == null
        || p.id() <= 0
        || p.lat() == null
        || p.lng() == null
        || !Double.isFinite(p.lat())
        || !Double.isFinite(p.lng())
        || p.lat() < 33
        || p.lat() > 39
        || p.lng() < 124
        || p.lng() > 132) throw ApiException.badRequest("유효한 관광지와 국내 좌표가 필요해요.");
    Validator.requireText(p.title(), 100, "장소명");
    Validator.optionalText(p.image(), 255, "이미지 주소");
    Validator.optionalText(p.address(), 100, "주소");
    if (p.contentTypeId() == null
        || !Set.of(12, 14, 15, 25, 28, 32, 38, 39).contains(p.contentTypeId()))
      throw ApiException.badRequest("장소 유형을 확인해주세요.");
  }

  public static void validate(PlanWrite p) {
    Validator.requireText(p.title(), 100, "제목");
    if (p.startDate() == null || p.endDate() == null) throw ApiException.badRequest("기간이 필요해요.");
    long days = ChronoUnit.DAYS.between(p.startDate(), p.endDate());
    if (days < 0
        || days > 13
        || p.capacity() == null
        || p.capacity() < 2
        || p.capacity() > 10
        || p.budget() == null
        || p.budget() < 0
        || p.items() == null
        || p.items().size() > 500) throw ApiException.badRequest("기간·정원·예산·일정을 확인해주세요.");
    Validator.optionalText(p.region(), 20, "지역");
    Validator.optionalText(p.theme(), 20, "테마");
    Validator.optionalText(p.description(), 1000, "소개");
    for (var s : p.items()) {
      if (s == null || s.day() == null || s.day() < 1 || s.day() > days + 1)
        throw ApiException.badRequest("일정 날짜가 여행 기간 밖이에요.");
      validatePlace(s.place());
      Validator.optionalText(s.memo(), 200, "메모");
      if (s.time() != null && !s.time().isBlank()) {
        try {
          LocalTime.parse(s.time());
        } catch (Exception e) {
          throw ApiException.badRequest("방문 시각을 확인해주세요.");
        }
      }
    }
  }

  public Object save(long id, long user, PlanWrite p) {
    validate(p);
    return Tx.write(
        c -> {
          if (id != 0) {
            var old = required(dao.plan(c, id, true));
            owner(old, user);
            if(p.version()==null)throw ApiException.badRequest("저장한 계획의 버전이 필요해요. 새로고침해주세요.");
            if(p.version()!=Sql.number(old,"version"))throw ApiException.conflict("다른 화면에서 계획이 변경됐어요. 새로고침 후 다시 수정해주세요.");
            com.mapmates.companion.service.TripLifecycle.editable(c,id);
            long n =
                Sql.number(
                    Sql.one(
                        c,
                        "SELECT 1+COUNT(*) AS n FROM companion_participants cp JOIN companions c ON"
                            + " c.id=cp.companion_id WHERE c.plan_id=?",
                        id),
                    "n");
            if (p.capacity() < n) throw ApiException.conflict("참가 인원보다 정원을 줄일 수 없어요.");
          }
          long key = dao.save(c, id, user, p);
          if(id!=0){
            var cp=Sql.one(c,"SELECT id FROM companions WHERE plan_id=?",id);
            String summary="여행계획 변경: "+p.title()+" · "+p.startDate()+" ~ "+p.endDate()+" · "+p.items().size()+"개 방문 일정";
            Sql.update(c,"INSERT INTO plan_revisions(plan_id,actor_id,summary,snapshot) VALUES(?,?,?,?)",id,user,summary,com.mapmates.common.web.JsonUtil.stringify(p));
            if(cp!=null)com.mapmates.notification.Notifications.participants(c,Sql.number(cp,"id"),user,"plan_changed",summary);
          }
          return decorate(c, dao.plan(c, key, false));
        });
  }

  public void deletePlan(long id, long user) {
    Tx.write(
        c -> {
          owner(required(dao.plan(c, id, true)), user);
          return Sql.update(c, "DELETE FROM plans WHERE id=?", id);
        });
  }

  private Map<String, Object> detail(Connection c, long id) throws SQLException {
    var item = required(dao.companion(c, id));
    var p = required(dao.plan(c, Sql.number(item, "tripId"), false));
    var full = decorate(c, p);
    var host = Sql.one(c, "SELECT nickname,photo FROM members WHERE id=?", p.get("ownerId"));
    item.putAll(p);
    item.put("lifecycle",com.mapmates.companion.service.TripLifecycle.effective(item));
    item.put("id", id);
    item.put("tripId", String.valueOf(p.get("id")));
    item.put("ownerId", String.valueOf(p.get("ownerId")));
    item.put("sourceTrip", p.get("title"));
    item.put("author", host.get("nickname"));
    item.put("avatar", host.get("nickname"));
    item.put("participants", dao.participants(c, id));
    int n = ((List<?>) item.get("participants")).size();
    item.put("people", n + "/" + p.get("capacity") + "명");
    boolean closed =
        Boolean.TRUE.equals(item.get("closed")) || Integer.valueOf(1).equals(item.get("closed"));
    item.put("closed", closed);
    item.put(
        "status",
        closed || java.util.Set.of("completed","cancelled").contains(item.get("lifecycle")) || n >= Sql.number(p, "capacity")
            ? "closed"
            : Sql.number(p, "capacity") - n <= 1 ? "soon" : "open");
    item.put("dates", p.get("startDate") + " — " + p.get("endDate"));
    item.put(
        "tags",
        List.of(Objects.toString(p.get("region"), "여행"), Objects.toString(p.get("theme"), "자유")));
    item.put("schedule", full.get("tripSchedule"));
    item.put("places", full.get("itineraryPlaces"));
    var stops = (List<Map<String,Object>>)full.get("tripSchedule");
    item.put(
        "image",
        stops.isEmpty()
            ? "./assets/sunset-clouds.png"
            : Objects.toString(stops.get(0).get("image"), ""));
    return item;
  }

  public Object companion(long id) {
    return Tx.read(c -> detail(c, id));
  }

  public Object companions(ApiRequest r) {
    var viewer=r.loginUser();long user=viewer==null?0:viewer.id();
    if("me".equals(r.query("related"))&&user==0)throw ApiException.unauthorized("로그인이 필요해요.");
    return Tx.read(c->{
      String where="1=1";var args=new ArrayList<Object>();
      for(String[] f:new String[][]{{"region","p.region"},{"theme","p.theme"}})if(r.query(f[0])!=null){where+=" AND "+f[1]+"=?";args.add(r.query(f[0]));}
      if(r.query("keyword")!=null){where+=" AND (p.title LIKE ? OR p.description LIKE ? OR m.nickname LIKE ?)";for(int i=0;i<3;i++)args.add("%"+Validator.requireText(r.query("keyword"),100,"검색어")+"%");}
      if("me".equals(r.query("related"))){where+=" AND (p.owner_id=? OR EXISTS(SELECT 1 FROM companion_participants mine WHERE mine.companion_id=c.id AND mine.member_id=?))";args.add(user);args.add(user);}
      String open="(c.status='open' AND c.lifecycle='scheduled' AND p.start_date>=CURDATE() AND (SELECT COUNT(*)+1 FROM companion_participants cap WHERE cap.companion_id=c.id)<p.capacity)";
      if(r.query("status")!=null){String status=Validator.requireOneOf(r.query("status"),"모집 상태","open","closed","soon");where+=" AND "+(status.equals("closed")?"NOT "+open:status.equals("soon")?open+" AND (SELECT COUNT(*)+1 FROM companion_participants cap WHERE cap.companion_id=c.id)=p.capacity-1":open);}
      int size=r.queryInt("size",20,1,100),page=r.queryInt("page",1,1,100000);
      String from=" FROM companions c JOIN plans p ON p.id=c.plan_id JOIN members m ON m.id=p.owner_id WHERE ";
      long total=Sql.number(Sql.one(c,"SELECT COUNT(*) n"+from+where,args.toArray()),"n");args.add(size);args.add((page-1)*size);
      var rows=Sql.rows(c,"SELECT c.id,c.plan_id AS planKey,c.status='closed' AS closed,c.join_policy AS joinPolicy,c.lifecycle,p.owner_id AS ownerId,p.title,p.start_date AS startDate,p.end_date AS endDate,p.capacity,p.budget,p.region,p.theme,p.description,p.version,m.nickname AS author,m.photo AS hostPhoto,m.gender AS hostGender"+from+where+" ORDER BY c.created_at DESC,c.id DESC LIMIT ? OFFSET ?",args.toArray());
      if(rows.isEmpty())return Map.of("items",List.of(),"page",page,"size",size,"total",total);
      String marks=String.join(",",Collections.nCopies(rows.size(),"?"));Object[] planIds=rows.stream().map(v->v.get("planKey")).toArray(),ids=rows.stream().map(v->v.get("id")).toArray();
      var stops=Sql.rows(c,"SELECT pi.plan_id AS planKey,pi.content_id AS placeId,pi.day_no AS day,pi.visit_time AS time,pi.memo,a.title,a.latitude AS lat,a.longitude AS lng,a.first_image1 AS image,a.addr1 AS address,a.content_type_id AS contentTypeId FROM plan_items pi JOIN attractions a ON a.content_id=pi.content_id WHERE pi.plan_id IN ("+marks+") ORDER BY pi.plan_id,pi.day_no,pi.visit_time,pi.sort_order",planIds);
      var people=Sql.rows(c,"SELECT cp.companion_id AS companionId,CAST(m.id AS CHAR) AS id,m.nickname,m.photo,m.gender FROM companion_participants cp JOIN members m ON m.id=cp.member_id WHERE cp.companion_id IN ("+marks+") ORDER BY cp.joined_at,cp.member_id",ids);
      var byPlan=new HashMap<Long,List<Map<String,Object>>>();for(var stop:stops)byPlan.computeIfAbsent(Sql.number(stop,"planKey"),k->new ArrayList<>()).add(stop);
      var byCompanion=new HashMap<Long,List<Map<String,Object>>>();for(var person:people)byCompanion.computeIfAbsent(Sql.number(person,"companionId"),k->new ArrayList<>()).add(person);
      var out=new ArrayList<Map<String,Object>>();for(var v:rows){long id=Sql.number(v,"id"),plan=Sql.number(v,"planKey"),owner=Sql.number(v,"ownerId");
       var entries=byPlan.getOrDefault(plan,List.of());var places=new LinkedHashMap<String,Object>();for(var stop:entries){var place=new LinkedHashMap<>(stop);place.put("id",stop.get("placeId"));places.put(stop.get("placeId").toString(),place);}
       var participants=new ArrayList<Map<String,Object>>();var host=new LinkedHashMap<String,Object>();host.put("id",String.valueOf(owner));host.put("nickname",v.get("author"));host.put("photo",v.get("hostPhoto"));host.put("gender",v.get("hostGender"));participants.add(host);participants.addAll(byCompanion.getOrDefault(id,List.of()));
       v.put("ownerId",String.valueOf(owner));v.put("tripId",String.valueOf(plan));v.put("schedule",entries);v.put("places",places);v.put("participants",participants);v.put("people",participants.size()+"/"+v.get("capacity")+"명");v.put("sourceTrip",v.get("title"));v.put("avatar",v.get("author"));v.put("dates",v.get("startDate")+" — "+v.get("endDate"));v.put("tags",List.of(Objects.toString(v.get("region"),"여행"),Objects.toString(v.get("theme"),"자유")));v.put("image",entries.isEmpty()?"":Objects.toString(entries.get(0).get("image"),""));v.put("lifecycle",com.mapmates.companion.service.TripLifecycle.effective(v));
       boolean closed=Boolean.TRUE.equals(v.get("closed"))||Integer.valueOf(1).equals(v.get("closed"));v.put("closed",closed);v.put("status",closed||Set.of("completed","cancelled").contains(v.get("lifecycle"))||participants.size()>=Sql.number(v,"capacity")?"closed":Sql.number(v,"capacity")-participants.size()<=1?"soon":"open");out.add(v);
      }return Map.of("items",out,"page",page,"size",size,"total",total);
    });
  }

  public Object publish(long user, CompanionWrite p) {
    if (p.planId() == null) throw ApiException.badRequest("여행계획을 선택해주세요.");
    return Tx.write(
        c -> {
          var plan = required(dao.plan(c, p.planId(), true));
          owner(plan, user);
          if (dao.items(c, p.planId()).isEmpty()) throw ApiException.badRequest("장소를 먼저 추가해주세요.");
          var old = Sql.one(c, "SELECT id FROM companions WHERE plan_id=?", p.planId());
          long id =
              old == null
                  ? Sql.insert(c, "INSERT INTO companions(plan_id) VALUES(?)", p.planId())
                  : Sql.number(old, "id");
          return detail(c, id);
        });
  }

  // All participant/capacity/closure changes lock the plan first: one shared lock order.
  private Map<String, Object> lock(Connection c, long id) throws SQLException {
    var v = required(dao.companion(c, id));
    required(dao.plan(c, Sql.number(v, "tripId"), true));
    return required(dao.companion(c, id));
  }

  public Object edit(long id, long user, CompanionWrite p) {
    return Tx.write(
        c -> {
          var v = lock(c, id);
          owner(v, user);
          com.mapmates.companion.service.TripLifecycle.editable(c,Sql.number(v,"tripId"));
          var currentPlan=dao.plan(c,Sql.number(v,"tripId"),false);
          boolean metadata=p.title()!=null||p.description()!=null||p.capacity()!=null;
          if(metadata&&(p.expectedVersion()==null||p.expectedVersion()!=Sql.number(currentPlan,"version")))throw ApiException.conflict("여행계획이 변경됐어요. 새로고침해주세요.");
          int n = dao.participants(c, id).size();
          if (p.capacity() != null) {
            if (p.capacity() < n || p.capacity() < 2 || p.capacity() > 10)
              throw ApiException.conflict("정원을 확인해주세요.");
            Sql.update(c, "UPDATE plans SET capacity=? WHERE id=?", p.capacity(), v.get("tripId"));
          }
          if (p.title() != null)
            Sql.update(
                c,
                "UPDATE plans SET title=? WHERE id=?",
                Validator.requireText(p.title(), 100, "제목"),
                v.get("tripId"));
          if (p.description() != null)
            Sql.update(
                c,
                "UPDATE plans SET description=? WHERE id=?",
                Validator.requireText(p.description(), 1000, "소개"),
                v.get("tripId"));
          if(p.joinPolicy()!=null){Validator.requireOneOf(p.joinPolicy(),"참가 방식","instant","approval");Sql.update(c,"UPDATE companions SET join_policy=? WHERE id=?",p.joinPolicy(),id);}
          if(metadata)Sql.update(c,"UPDATE plans SET version=version+1 WHERE id=?",v.get("tripId"));
          if (p.closed() != null)
            Sql.update(
                c, "UPDATE companions SET status=? WHERE id=?", p.closed() ? "closed" : "open", id);
          return detail(c, id);
        });
  }

  public void deleteCompanion(long id, long user) {
    Tx.write(
        c -> {
          owner(lock(c, id), user);
          return Sql.update(c, "DELETE FROM companions WHERE id=?", id);
        });
  }

  public Object participate(long id, long user, boolean join) {
    return Tx.write(
        c -> {
          var v = lock(c, id);
          if (Sql.number(v, "ownerId") == user) throw ApiException.conflict("모집자는 이미 참가 중이에요.");
          boolean exists =
              Sql.one(
                      c,
                      "SELECT 1 FROM companion_participants WHERE companion_id=? AND member_id=?",
                      id,
                      user)
                  != null;
          if (join && !exists) {
            var p = dao.plan(c, Sql.number(v, "tripId"), false);
            var status = Sql.one(c, "SELECT status FROM companions WHERE id=?", id);
            if ("closed".equals(status.get("status"))
                || dao.participants(c, id).size() >= Sql.number(p, "capacity"))
              throw ApiException.conflict("모집이 마감되었어요.");
            Sql.update(
                c,
                "INSERT INTO companion_participants(companion_id,member_id) VALUES(?,?)",
                id,
                user);
          }
          if (!join)
            Sql.update(
                c,
                "DELETE FROM companion_participants WHERE companion_id=? AND member_id=?",
                id,
                user);
          return detail(c, id);
        });
  }

  public Object favorites(long user) {
    return Tx.read(
        c ->
            Sql.rows(
                c,
                "SELECT a.content_id AS id,a.title,a.latitude AS lat,a.longitude AS"
                    + " lng,a.first_image1 AS image,a.content_type_id AS contentTypeId,a.addr1 AS"
                    + " address FROM favorites f JOIN attractions a ON a.content_id=f.content_id"
                    + " WHERE f.member_id=? ORDER BY f.created_at DESC",
                user));
  }

  public void favorite(long user, PlaceWrite p) {
    validatePlace(p);
    Tx.write(
        c -> {
          new CatalogDaoImpl().ensure(c, p);
          return Sql.update(
              c,
              "INSERT INTO favorites(member_id,content_id) VALUES(?,?) ON DUPLICATE KEY UPDATE"
                  + " member_id=VALUES(member_id)",
              user,
              p.id());
        });
  }

  public void unfavorite(long user, int id) {
    Tx.write(
        c -> Sql.update(c, "DELETE FROM favorites WHERE member_id=? AND content_id=?", user, id));
  }
}
