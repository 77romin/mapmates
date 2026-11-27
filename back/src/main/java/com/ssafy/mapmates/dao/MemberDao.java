package com.ssafy.mapmates.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ssafy.mapmates.dto.Member;

@Mapper
public interface MemberDao {
    @Insert("INSERT INTO members (user_id, password, name, email, role, tags, home_lat, home_lng) "
            + "VALUES (#{userId}, #{password}, #{name}, #{email}, COALESCE(#{role}, 'USER'), #{tags}, #{homeLat}, #{homeLng})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Member member);

    @Select("SELECT * FROM members WHERE user_id = #{userId}")
    Member findByUserId(String userId);

    @Select("SELECT * FROM members WHERE id = #{id}")
    Member findById(int id);

    @Update("UPDATE members SET name = #{name}, email = #{email}, tags = #{tags}, home_lat = #{homeLat}, home_lng = #{homeLng} WHERE id = #{id}")
    int update(Member member);

    @Update("UPDATE members SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") int id, @Param("password") String password);

    @Delete("DELETE FROM members WHERE id = #{id}")
    int delete(int id);

    @Select("SELECT COUNT(*) FROM members")
    int count();
}
