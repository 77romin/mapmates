package com.mapmates.common.db;

import java.sql.*;
import java.util.*;

/** Prepared statements only; SQL structure is supplied by trusted DAOs. */
public final class Sql {
  private Sql() {}

  private static PreparedStatement statement(Connection c, String sql, Object... args)
      throws SQLException {
    PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
    return p;
  }

  public static List<Map<String, Object>> rows(Connection c, String sql, Object... args)
      throws SQLException {
    try (var p = statement(c, sql, args);
        var r = p.executeQuery()) {
      var out = new ArrayList<Map<String, Object>>();
      var meta = r.getMetaData();
      while (r.next()) {
        var row = new LinkedHashMap<String, Object>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
          Object v = r.getObject(i);
          if (v instanceof java.sql.Date d) v = d.toLocalDate().toString();
          else if (v instanceof Timestamp t) v = t.toLocalDateTime().toString();
          else if (v instanceof Time t) v = t.toLocalTime().toString();
          row.put(meta.getColumnLabel(i), v);
        }
        out.add(row);
      }
      return out;
    }
  }

  public static Map<String, Object> one(Connection c, String sql, Object... args)
      throws SQLException {
    var rows = rows(c, sql, args);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public static long insert(Connection c, String sql, Object... args) throws SQLException {
    try (var p = statement(c, sql, args)) {
      p.executeUpdate();
      try (var r = p.getGeneratedKeys()) {
        if (!r.next()) throw new SQLException("Missing generated key");
        return r.getLong(1);
      }
    }
  }

  public static int update(Connection c, String sql, Object... args) throws SQLException {
    try (var p = statement(c, sql, args)) {
      return p.executeUpdate();
    }
  }

  public static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }
}
