package com.ssafy.mapmates.dao;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ssafy.mapmates.dto.Board;
import com.ssafy.mapmates.dto.Comment;

@Mapper
public interface BoardDao {
    @Select("<script>SELECT b.id, b.type, b.member_id, b.title, b.hit, b.created_at, m.name AS writer_name, "
            + "(SELECT COUNT(*) FROM comments c WHERE c.board_id = b.id) AS comment_count "
            + "FROM boards b JOIN members m ON b.member_id = m.id WHERE b.type = #{type}"
            + "<if test=\"keyword != null and keyword != ''\"> AND (b.title LIKE CONCAT('%', #{keyword}, '%') OR b.content LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + " ORDER BY b.id DESC LIMIT #{offset}, #{size}</script>")
    List<Board> findAll(@Param("type") String type, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM boards b WHERE b.type = #{type}"
            + "<if test=\"keyword != null and keyword != ''\"> AND (b.title LIKE CONCAT('%', #{keyword}, '%') OR b.content LIKE CONCAT('%', #{keyword}, '%'))</if></script>")
    int count(@Param("type") String type, @Param("keyword") String keyword);

    @Select("SELECT b.*, m.name AS writer_name FROM boards b JOIN members m ON b.member_id = m.id WHERE b.id = #{id}")
    Board findById(int id);

    @Insert("INSERT INTO boards (type, member_id, title, content) VALUES (#{type}, #{memberId}, #{title}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Board board);

    @Update("UPDATE boards SET title = #{title}, content = #{content} WHERE id = #{id}")
    int update(Board board);

    @Update("UPDATE boards SET hit = hit + 1 WHERE id = #{id}")
    int increaseHit(int id);

    @Delete("DELETE FROM boards WHERE id = #{id}")
    int delete(int id);

    @Select("SELECT c.*, m.name AS writer_name FROM comments c JOIN members m ON c.member_id = m.id WHERE c.board_id = #{boardId} ORDER BY c.id")
    List<Comment> findComments(int boardId);

    @Select("SELECT * FROM comments WHERE id = #{id}")
    Comment findComment(int id);

    @Insert("INSERT INTO comments (board_id, member_id, content) VALUES (#{boardId}, #{memberId}, #{content})")
    int insertComment(Comment comment);

    @Delete("DELETE FROM comments WHERE id = #{id}")
    int deleteComment(int id);

    @Select("SELECT COUNT(*) FROM boards")
    int countAll();
}
