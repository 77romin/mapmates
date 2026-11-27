package com.ssafy.mapmates.dao;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ssafy.mapmates.dto.Member;
import com.ssafy.mapmates.dto.Plan;
import com.ssafy.mapmates.dto.PlanItem;

@Mapper
public interface PlanDao {
    String PLAN_COLS = "p.*, m.name AS owner_name, (SELECT COUNT(*) FROM plan_items i WHERE i.plan_id = p.id) AS item_count ";

    @Insert("INSERT INTO plans (member_id, title, start_date, end_date, budget, memo, is_public) "
            + "VALUES (#{memberId}, #{title}, #{startDate}, #{endDate}, COALESCE(#{budget}, 0), #{memo}, COALESCE(#{isPublic}, 0))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Plan plan);

    @Update("UPDATE plans SET title = #{title}, start_date = #{startDate}, end_date = #{endDate}, "
            + "budget = COALESCE(#{budget}, 0), memo = #{memo}, is_public = COALESCE(#{isPublic}, 0) WHERE id = #{id}")
    int update(Plan plan);

    @Delete("DELETE FROM plans WHERE id = #{id}")
    int delete(int id);

    @Select("SELECT " + PLAN_COLS + "FROM plans p JOIN members m ON p.member_id = m.id WHERE p.id = #{id}")
    Plan findById(int id);

    /** 내가 만들었거나 메이트로 참여 중인 계획 */
    @Select("SELECT " + PLAN_COLS + "FROM plans p JOIN members m ON p.member_id = m.id "
            + "WHERE p.id IN (SELECT plan_id FROM plan_members WHERE member_id = #{memberId}) ORDER BY p.id DESC")
    List<Plan> findMine(int memberId);

    @Select("SELECT " + PLAN_COLS + "FROM plans p JOIN members m ON p.member_id = m.id WHERE p.is_public = 1 ORDER BY p.id DESC LIMIT 50")
    List<Plan> findPublic();

    @Select("SELECT i.*, a.title, a.addr1, a.first_image1, a.latitude, a.longitude, c.content_type_name "
            + "FROM plan_items i JOIN attractions a ON i.attraction_no = a.no "
            + "LEFT JOIN contenttypes c ON a.content_type_id = c.content_type_id "
            + "WHERE i.plan_id = #{planId} ORDER BY i.day_no, i.seq, i.id")
    List<PlanItem> findItems(int planId);

    @Delete("DELETE FROM plan_items WHERE plan_id = #{planId}")
    int deleteItems(int planId);

    @Insert("INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) "
            + "VALUES (#{planId}, COALESCE(#{dayNo}, 1), #{seq}, #{attractionNo}, #{memo}, COALESCE(#{cost}, 0))")
    int insertItem(PlanItem item);

    @Insert("INSERT IGNORE INTO plan_members (plan_id, member_id, role) VALUES (#{planId}, #{memberId}, #{role})")
    int addMember(@Param("planId") int planId, @Param("memberId") int memberId, @Param("role") String role);

    @Select("SELECT COUNT(*) FROM plan_members WHERE plan_id = #{planId} AND member_id = #{memberId}")
    int isMember(@Param("planId") int planId, @Param("memberId") int memberId);

    @Select("SELECT m.id, m.user_id, m.name, m.tags, m.home_lat, m.home_lng, pm.role FROM plan_members pm "
            + "JOIN members m ON pm.member_id = m.id WHERE pm.plan_id = #{planId} ORDER BY pm.joined_at")
    List<Member> findMembers(int planId);
}
