package com.ssafy.mapmates.dao;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ssafy.mapmates.dto.MateApplication;
import com.ssafy.mapmates.dto.MatePost;

@Mapper
public interface MateDao {
    String COLS = "mp.*, m.name AS writer_name, p.title AS plan_title, p.start_date, p.end_date, "
            + "(SELECT COUNT(*) FROM plan_members pm WHERE pm.plan_id = mp.plan_id) AS joined_count, "
            + "(SELECT COUNT(*) FROM plan_items pi WHERE pi.plan_id = mp.plan_id) AS place_count, "
            // 카드 표지용: 방문 순서대로 사진 주소를 '|' 로 이어 붙인다 (GROUP_CONCAT 기본 길이 제한 안에서 앞쪽 몇 장이면 충분하다)
            + "(SELECT GROUP_CONCAT(a.first_image1 ORDER BY pi.day_no, pi.seq SEPARATOR '|') FROM plan_items pi "
            + "JOIN attractions a ON pi.attraction_no = a.no "
            + "WHERE pi.plan_id = mp.plan_id AND a.first_image1 IS NOT NULL AND a.first_image1 <> '') AS cover_images ";
    String FROM = "FROM mate_posts mp JOIN members m ON mp.member_id = m.id JOIN plans p ON mp.plan_id = p.id ";

    @Select("SELECT " + COLS + FROM + "ORDER BY (mp.status = 'OPEN') DESC, mp.id DESC LIMIT 100")
    List<MatePost> findAll();

    @Select("SELECT " + COLS + FROM + "WHERE mp.id = #{id}")
    MatePost findById(int id);

    @Insert("INSERT INTO mate_posts (plan_id, member_id, title, content, capacity, tags) "
            + "VALUES (#{planId}, #{memberId}, #{title}, #{content}, COALESCE(#{capacity}, 4), #{tags})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MatePost post);

    @Update("UPDATE mate_posts SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") int id, @Param("status") String status);

    @Delete("DELETE FROM mate_posts WHERE id = #{id}")
    int delete(int id);

    @Insert("INSERT INTO mate_applications (post_id, member_id, message) VALUES (#{postId}, #{memberId}, #{message})")
    int apply(MateApplication app);

    @Select("SELECT a.*, m.name AS member_name, m.tags AS member_tags FROM mate_applications a "
            + "JOIN members m ON a.member_id = m.id WHERE a.post_id = #{postId} ORDER BY a.id")
    List<MateApplication> findApplications(int postId);

    @Select("SELECT * FROM mate_applications WHERE id = #{id}")
    MateApplication findApplication(int id);

    @Select("SELECT status FROM mate_applications WHERE post_id = #{postId} AND member_id = #{memberId}")
    String findMyStatus(@Param("postId") int postId, @Param("memberId") int memberId);

    @Update("UPDATE mate_applications SET status = #{status} WHERE id = #{id}")
    int updateApplication(@Param("id") int id, @Param("status") String status);
}
