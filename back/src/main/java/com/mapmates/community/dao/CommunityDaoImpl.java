package com.mapmates.community.dao;

import com.mapmates.common.db.Sql;
import java.sql.*;
import java.util.*;

public final class CommunityDaoImpl implements CommunityDao {
  public static final String POST =
      "p.id,CAST(p.owner_id AS CHAR) AS ownerId,p.board,p.category,p.title,p.content,p.view_count"
          + " AS views,DATE_FORMAT(p.created_at,'%Y.%m.%d') AS date,p.updated_at AS"
          + " updatedAt,m.nickname AS author";

  public Map<String, Object> post(Connection c, long id, boolean lock) throws SQLException {
    return Sql.one(
        c,
        "SELECT "
            + POST
            + " FROM posts p JOIN members m ON m.id=p.owner_id WHERE p.id=?"
            + (lock ? " FOR UPDATE" : ""),
        id);
  }

  public List<Map<String, Object>> comments(Connection c, String column, long id)
      throws SQLException {
    if (!Set.of("post_id", "companion_id").contains(column)) throw new IllegalArgumentException();
    return Sql.rows(
        c,
        "SELECT CAST(c.id AS CHAR) AS id,CAST(c.owner_id AS CHAR) AS ownerId,CAST(c.parent_id AS"
            + " CHAR) AS parentId,c.content,c.deleted,c.created_at AS"
            + " createdAt,IF(c.updated_at=c.created_at,NULL,c.updated_at) AS"
            + " updatedAt,m.nickname,m.photo,m.gender,r.nickname AS replyTo FROM comments c JOIN"
            + " members m ON m.id=c.owner_id LEFT JOIN members r ON r.id=c.reply_to_member_id WHERE"
            + " c."
            + column
            + "=? ORDER BY c.created_at,c.id",
        id);
  }
}
