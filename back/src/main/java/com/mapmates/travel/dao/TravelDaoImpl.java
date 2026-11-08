package com.mapmates.travel.dao;

import com.mapmates.catalog.dao.CatalogDaoImpl;
import com.mapmates.common.db.Sql;
import com.mapmates.travel.dto.*;
import java.sql.*;
import java.util.*;

public final class TravelDaoImpl implements TravelDao {
  private static final String FIELDS =
      "id,owner_id AS ownerId,title,start_date AS startDate,end_date AS"
          + " endDate,capacity,budget,region,theme,description,version";

  public Map<String, Object> plan(Connection c, long id, boolean lock) throws SQLException {
    return Sql.one(
        c, "SELECT " + FIELDS + " FROM plans WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
  }

  public List<Map<String, Object>> plans(Connection c, long owner) throws SQLException {
    return Sql.rows(
        c,
        "SELECT " + FIELDS + " FROM plans WHERE owner_id=? ORDER BY updated_at DESC,id DESC",
        owner);
  }

  public long save(Connection c, long id, long owner, PlanWrite p) throws SQLException {
    if (id == 0)
      id =
          Sql.insert(
              c,
              "INSERT INTO"
                  + " plans(owner_id,title,start_date,end_date,capacity,budget,region,theme,description)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)",
              owner,
              p.title().trim(),
              p.startDate(),
              p.endDate(),
              p.capacity(),
              p.budget(),
              p.region(),
              p.theme(),
              p.description());
    else {
      Sql.update(
          c,
          "UPDATE plans SET"
              + " version=version+1,title=?,start_date=?,end_date=?,capacity=?,budget=?,region=?,theme=?,description=?"
              + " WHERE id=?",
          p.title().trim(),
          p.startDate(),
          p.endDate(),
          p.capacity(),
          p.budget(),
          p.region(),
          p.theme(),
          p.description(),
          id);
      Sql.update(c, "DELETE FROM plan_items WHERE plan_id=?", id);
    }
    int order = 0;
    for (var stop : p.items()) {
      new CatalogDaoImpl().ensure(c, stop.place());
      Sql.update(
          c,
          "INSERT INTO plan_items(plan_id,content_id,day_no,visit_time,sort_order,memo)"
              + " VALUES(?,?,?,?,?,?)",
          id,
          stop.place().id(),
          stop.day(),
          stop.time() == null || stop.time().isBlank() ? null : stop.time(),
          order++,
          stop.memo());
    }
    return id;
  }

  public List<Map<String, Object>> items(Connection c, long id) throws SQLException {
    return Sql.rows(
        c,
        "SELECT pi.content_id AS placeId,pi.day_no AS day,pi.visit_time AS"
            + " time,pi.memo,a.title,a.latitude AS lat,a.longitude AS lng,a.first_image1 AS"
            + " image,a.addr1 AS address,a.content_type_id AS contentTypeId FROM plan_items pi JOIN"
            + " attractions a ON a.content_id=pi.content_id WHERE pi.plan_id=? ORDER BY"
            + " pi.day_no,pi.visit_time,pi.sort_order",
        id);
  }

  public Map<String, Object> companion(Connection c, long id) throws SQLException {
    return Sql.one(
        c,
        "SELECT c.id,c.plan_id AS tripId,c.status='closed' AS closed,c.join_policy AS joinPolicy,c.lifecycle,p.owner_id AS ownerId FROM"
            + " companions c JOIN plans p ON p.id=c.plan_id WHERE c.id=?",
        id);
  }

  public Map<Long, List<Map<String, Object>>> itemsByPlan(Connection c, List<Long> ids)
      throws SQLException {
    var grouped = new HashMap<Long, List<Map<String, Object>>>();
    // Chunk large accounts to keep the IN list and bind count bounded.
    for (int from = 0; from < ids.size(); from += 500) {
      var chunk = ids.subList(from, Math.min(from + 500, ids.size()));
      String marks = String.join(",", Collections.nCopies(chunk.size(), "?"));
      var rows = Sql.rows(c,
          "SELECT pi.plan_id AS planId,pi.content_id AS placeId,pi.day_no AS day,"
              + "pi.visit_time AS time,pi.memo,a.title,a.latitude AS lat,a.longitude AS lng,"
              + "a.first_image1 AS image,a.addr1 AS address,a.content_type_id AS contentTypeId "
              + "FROM plan_items pi JOIN attractions a ON a.content_id=pi.content_id "
              + "WHERE pi.plan_id IN (" + marks + ") "
              + "ORDER BY pi.plan_id,pi.day_no,pi.visit_time,pi.sort_order",
          chunk.toArray());
      for (var row : rows) {
        long planId = Sql.number(row, "planId");
        row.remove("planId");
        grouped.computeIfAbsent(planId, key -> new ArrayList<>()).add(row);
      }
    }
    return grouped;
  }

  public List<Map<String, Object>> participants(Connection c, long id) throws SQLException {
    return Sql.rows(
        c,
        "SELECT CAST(m.id AS CHAR) AS id,m.nickname,m.photo,m.gender FROM members m JOIN (SELECT"
            + " p.owner_id AS member_id FROM companions c JOIN plans p ON p.id=c.plan_id WHERE"
            + " c.id=? UNION ALL SELECT member_id FROM companion_participants WHERE companion_id=?)"
            + " x ON x.member_id=m.id",
        id,
        id);
  }
}
