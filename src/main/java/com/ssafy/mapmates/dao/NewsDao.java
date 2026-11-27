package com.ssafy.mapmates.dao;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ssafy.mapmates.dto.News;

@Mapper
public interface NewsDao {
    @Insert("INSERT IGNORE INTO news (keyword, title, link, source, pub_date) VALUES (#{keyword}, #{title}, #{link}, #{source}, #{pubDate})")
    int insert(News news);

    @Select("SELECT * FROM news WHERE keyword = #{keyword} ORDER BY id DESC LIMIT #{size}")
    List<News> findByKeyword(@Param("keyword") String keyword, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM news WHERE keyword = #{keyword} AND created_at > NOW() - INTERVAL 1 HOUR")
    int countRecent(String keyword);
}
