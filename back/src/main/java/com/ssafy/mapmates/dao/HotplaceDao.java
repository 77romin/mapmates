package com.ssafy.mapmates.dao;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ssafy.mapmates.dto.Hotplace;

@Mapper
public interface HotplaceDao {
    @Select("SELECT h.*, m.name AS writer_name FROM hotplaces h JOIN members m ON h.member_id = m.id ORDER BY h.id DESC LIMIT 200")
    List<Hotplace> findAll();

    @Select("SELECT h.*, m.name AS writer_name FROM hotplaces h JOIN members m ON h.member_id = m.id WHERE h.id = #{id}")
    Hotplace findById(int id);

    @Insert("INSERT INTO hotplaces (member_id, title, description, place_type, visited_date, latitude, longitude, image_path) "
            + "VALUES (#{memberId}, #{title}, #{description}, #{placeType}, #{visitedDate}, #{latitude}, #{longitude}, #{imagePath})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Hotplace hotplace);

    @Update("UPDATE hotplaces SET title = #{title}, description = #{description}, place_type = #{placeType}, "
            + "visited_date = #{visitedDate} WHERE id = #{id}")
    int update(Hotplace hotplace);

    @Delete("DELETE FROM hotplaces WHERE id = #{id}")
    int delete(int id);
}
