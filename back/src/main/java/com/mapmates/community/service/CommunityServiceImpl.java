package com.mapmates.community.service;

import com.mapmates.common.db.*;
import com.mapmates.common.exception.ApiException;
import com.mapmates.common.security.SessionUser;
import com.mapmates.common.web.*;
import com.mapmates.community.dao.*;
import com.mapmates.community.dto.*;
import java.sql.*;
import java.util.*;

public final class CommunityServiceImpl implements CommunityService {
  private final CommunityDao dao = new CommunityDaoImpl();

  private Map<String, Object> required(Map<String, Object> v) {
    if (v == null) throw ApiException.notFound("콘텐츠를 찾을 수 없어요.");
    return v;
  }

  private void owner(Object owner, SessionUser u) {
    if (!String.valueOf(u.id()).equals(String.valueOf(owner)) && !"admin".equals(u.role()))
      throw new ApiException(403, "FORBIDDEN", "변경 권한이 없어요.");
  }

  private void writable(String board, SessionUser u) {
    Validator.requireOneOf(board, "게시판", "travel", "news", "notice");
    if (!"travel".equals(board) && !"admin".equals(u.role()))
      throw new ApiException(403, "FORBIDDEN", "운영자만 작성할 수 있어요.");
  }

  public Object list(ApiRequest r) {
    return Tx.read(
        c -> {
          String where = "1=1";
          var args = new ArrayList<Object>();
          if (r.query("board") != null) {
            where += " AND p.board=?";
            args.add(Validator.requireOneOf(r.query("board"), "게시판", "travel", "notice", "news"));
          }
          if (r.query("category") != null) {
            where += " AND p.category=?";
            args.add(r.query("category"));
          }
          if (r.query("keyword") != null) {
            String q = Validator.requireText(r.query("keyword"), 100, "검색어");
            where += " AND (p.title LIKE ? OR p.content LIKE ? OR p.category LIKE ? OR EXISTS (SELECT 1 FROM members author WHERE author.id=p.owner_id AND author.nickname LIKE ?))";
            args.add("%" + q + "%");
            args.add("%" + q + "%");
            args.add("%" + q + "%");
            args.add("%" + q + "%");
          }
          String sort = Objects.toString(r.query("sort"), "latest");
          String order =
              switch (sort) {
                case "latest" -> "p.created_at DESC,p.id DESC";
                case "views" -> "p.view_count DESC,p.id DESC";
                case "comments" -> "commentCount DESC,p.id DESC";
                default -> throw ApiException.badRequest("정렬 기준을 확인해주세요.");
              };
          int page = r.queryInt("page", 1, 1, 100000), size = r.queryInt("size", 20, 1, 100);
          long total =
              Sql.number(
                  Sql.one(c, "SELECT COUNT(*) AS n FROM posts p WHERE " + where, args.toArray()),
                  "n");
          args.add(size);
          args.add((page - 1) * size);
          var items =
              Sql.rows(
                  c,
                  "SELECT "
                      + CommunityDaoImpl.POST
                      + ",(SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id AND"
                      + " c.deleted=false) AS commentCount FROM posts p JOIN members m ON"
                      + " m.id=p.owner_id WHERE "
                      + where
                      + " ORDER BY "
                      + order
                      + " LIMIT ? OFFSET ?",
                  args.toArray());
          return Map.of("items", items, "total", total, "page", page, "size", size);
        });
  }

  public Object post(long id, boolean view) {
    return Tx.write(
        c -> {
          if (view) Sql.update(c, "UPDATE posts SET view_count=view_count+1 WHERE id=?", id);
          return required(dao.post(c, id, false));
        });
  }

  public Object save(long id, SessionUser u, PostWrite p) {
    writable(p.board(), u);
    String title = Validator.requireText(p.title(), 100, "제목"),
        content = Validator.requireText(p.content(), 5000, "내용"),
        category = Validator.requireText(p.category(), 20, "분류");
    return Tx.write(
        c -> {
          long key = id;
          if (id == 0)
            key =
                Sql.insert(
                    c,
                    "INSERT INTO posts(owner_id,board,category,title,content) VALUES(?,?,?,?,?)",
                    u.id(),
                    p.board(),
                    category,
                    title,
                    content);
          else {
            var old = required(dao.post(c, id, true));
            owner(old.get("ownerId"), u);
            writable((String) old.get("board"), u);
            Sql.update(
                c,
                "UPDATE posts SET board=?,category=?,title=?,content=?,updated_at=NOW() WHERE id=?",
                p.board(),
                category,
                title,
                content,
                id);
          }
          return dao.post(c, key, false);
        });
  }

  public void deletePost(long id, SessionUser u) {
    Tx.write(
        c -> {
          var old = required(dao.post(c, id, true));
          owner(old.get("ownerId"), u);
          return Sql.update(c, "DELETE FROM posts WHERE id=?", id);
        });
  }

  private String column(boolean companion) {
    return companion ? "companion_id" : "post_id";
  }

  private void target(Connection c, boolean companion, long id) throws SQLException {
    required(
        Sql.one(
            c,
            "SELECT id FROM " + (companion ? "companions" : "posts") + " WHERE id=? FOR UPDATE",
            id));
  }

  public Object comments(boolean companion, long id) {
    return Tx.read(c -> dao.comments(c, column(companion), id));
  }

  public Object addComment(boolean companion, long id, SessionUser u, CommentWrite p) {
    String content = Validator.requireText(p.content(), 500, "댓글");
    return Tx.write(
        c -> {
          target(c, companion, id);
          Long root = null, reply = null;
          if (p.parentId() != null) {
            var parent =
                required(Sql.one(c, "SELECT * FROM comments WHERE id=? FOR UPDATE", p.parentId()));
            if (parent.get(column(companion)) == null
                || Sql.number(parent, column(companion)) != id
                || Boolean.TRUE.equals(parent.get("deleted")))
              throw ApiException.badRequest("같은 글의 삭제되지 않은 댓글에만 답글을 달 수 있어요.");
            root = parent.get("parent_id") == null ? p.parentId() : Sql.number(parent, "parent_id");
            reply = Sql.number(parent, "owner_id");
          }
          Sql.insert(
              c,
              "INSERT INTO comments(owner_id,"
                  + column(companion)
                  + ",parent_id,reply_to_member_id,content) VALUES(?,?,?,?,?)",
              u.id(),
              id,
              root,
              reply,
              content);
          return dao.comments(c, column(companion), id);
        });
  }

  // Lock target before comment, consistent with inserts and cascade deletion.
  private Map<String, Object> lockComment(Connection c, long id) throws SQLException {
    var v = required(Sql.one(c, "SELECT * FROM comments WHERE id=?", id));
    boolean cp = v.get("companion_id") != null;
    target(c, cp, Sql.number(v, column(cp)));
    return required(Sql.one(c, "SELECT * FROM comments WHERE id=? FOR UPDATE", id));
  }

  public Object editComment(long id, SessionUser u, CommentWrite p) {
    String text = Validator.requireText(p.content(), 500, "댓글");
    return Tx.write(
        c -> {
          var old = lockComment(c, id);
          if (Sql.number(old, "owner_id") != u.id())
            throw new ApiException(403, "FORBIDDEN", "내 댓글만 수정할 수 있어요.");
          if (Boolean.TRUE.equals(old.get("deleted"))) throw ApiException.conflict("삭제된 댓글이에요.");
          Sql.update(c, "UPDATE comments SET content=? WHERE id=?", text, id);
          return Map.of("id", String.valueOf(id), "content", text);
        });
  }

  public void deleteComment(long id, SessionUser u) {
    Tx.write(
        c -> {
          var old = lockComment(c, id);
          boolean cp = old.get("companion_id") != null;
          var target =
              cp
                  ? Sql.one(
                      c,
                      "SELECT p.owner_id AS owner FROM companions c JOIN plans p ON p.id=c.plan_id"
                          + " WHERE c.id=?",
                      old.get("companion_id"))
                  : Sql.one(
                      c, "SELECT owner_id AS owner FROM posts WHERE id=?", old.get("post_id"));
          if (Sql.number(old, "owner_id") != u.id()) owner(target.get("owner"), u);
          if (Sql.one(c, "SELECT id FROM comments WHERE parent_id=? LIMIT 1", id) != null)
            return Sql.update(c, "UPDATE comments SET content='',deleted=true WHERE id=?", id);
          return Sql.update(c, "DELETE FROM comments WHERE id=?", id);
        });
  }
}
