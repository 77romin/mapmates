package com.mapmates.catalog.dao;

import com.mapmates.catalog.dto.PlaceWrite;
import com.mapmates.common.db.Sql;
import java.sql.*;
import java.util.*;

public final class CatalogDaoImpl implements CatalogDao {
  public static final String FIELDS =
      "content_id AS id,title,latitude AS lat,longitude AS lng,first_image1 AS image,addr1 AS"
          + " address,content_type_id AS contentTypeId,area_code AS areaCode,si_gun_gu_code AS"
          + " sigunguCode";

  public List<Map<String, Object>> places(
      Connection c, String where, List<Object> args, int limit, int offset) throws SQLException {
    var a = new ArrayList<>(args);
    a.add(limit);
    a.add(offset);
    return Sql.rows(
        c,
        "SELECT "
            + FIELDS
            + " FROM attractions WHERE "
            + where
            + " ORDER BY content_id LIMIT ? OFFSET ?",
        a.toArray());
  }

  public long count(Connection c, String where, List<Object> args) throws SQLException {
    return Sql.number(
        Sql.one(c, "SELECT COUNT(*) AS n FROM attractions WHERE " + where, args.toArray()), "n");
  }

  public Map<String, Object> place(Connection c, int id) throws SQLException {
    return Sql.one(c, "SELECT " + FIELDS + " FROM attractions WHERE content_id=?", id);
  }

  public void ensure(Connection c, PlaceWrite p) throws SQLException {
    // Existing public data is authoritative. Client snapshots only fill missing places.
    Sql.update(
        c,
        "INSERT INTO"
            + " attractions(content_id,title,latitude,longitude,first_image1,addr1,content_type_id)"
            + " VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE content_id=VALUES(content_id)",
        p.id(),
        p.title(),
        p.lat(),
        p.lng(),
        p.image(),
        p.address(),
        p.contentTypeId());
  }
}
