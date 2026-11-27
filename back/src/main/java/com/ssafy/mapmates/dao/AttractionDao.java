package com.ssafy.mapmates.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ssafy.mapmates.dto.Attraction;

@Mapper
public interface AttractionDao {
    String COLS = "a.no, a.content_id, a.title, a.content_type_id, c.content_type_name, a.area_code, a.si_gun_gu_code, "
            + "s.sido_name, g.gugun_name, a.first_image1, a.latitude, a.longitude, a.tel, a.addr1, a.addr2 ";
    String JOINS = "FROM attractions a LEFT JOIN contenttypes c ON a.content_type_id = c.content_type_id "
            + "LEFT JOIN sidos s ON a.area_code = s.sido_code "
            + "LEFT JOIN guguns g ON a.area_code = g.sido_code AND a.si_gun_gu_code = g.gugun_code ";

    @Select("SELECT sido_code AS code, sido_name AS name FROM sidos ORDER BY sido_code")
    List<Map<String, Object>> sidos();

    @Select("SELECT gugun_code AS code, gugun_name AS name FROM guguns WHERE sido_code = #{sidoCode} ORDER BY gugun_name")
    List<Map<String, Object>> guguns(int sidoCode);

    @Select("SELECT content_type_id AS code, content_type_name AS name FROM contenttypes ORDER BY content_type_id")
    List<Map<String, Object>> contentTypes();

    /**
     * 유형별로 순위(rn)를 매긴 뒤 순위 순으로 정렬해, 유형을 고르지 않은 "전체" 검색에서
     * 관광지·문화시설·축제·음식점 등이 번갈아 나오게 한다. (유형을 하나 고르면 일반 정렬과 같다)
     * 유형 안에서는 사진이 있는 곳, (지도 영역 검색이면) 화면 중심에 가까운 곳이 먼저다.
     */
    @Select("<script>SELECT * FROM (SELECT " + COLS + ", ROW_NUMBER() OVER (PARTITION BY a.content_type_id ORDER BY"
            + " (a.first_image1 IS NULL OR a.first_image1 = ''),"
            + "<if test='minLat != null'> POW(a.latitude - (#{minLat} + #{maxLat}) / 2, 2) + POW(a.longitude - (#{minLng} + #{maxLng}) / 2, 2),</if>"
            + " a.no) AS rn " + JOINS
            + "<where>"
            + "<if test='sidoCode != null'>a.area_code = #{sidoCode}</if>"
            + "<if test='gugunCode != null'> AND a.si_gun_gu_code = #{gugunCode}</if>"
            + "<if test='contentTypeId != null'> AND a.content_type_id = #{contentTypeId}</if>"
            + "<if test=\"keyword != null and keyword != ''\"> AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.addr1 LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test='minLat != null'> AND a.latitude BETWEEN #{minLat} AND #{maxLat} AND a.longitude BETWEEN #{minLng} AND #{maxLng}</if>"
            + "</where>"
            + ") t ORDER BY (t.first_image1 IS NULL OR t.first_image1 = ''), t.rn, t.content_type_id LIMIT #{offset}, #{size}</script>")
    List<Attraction> search(@Param("sidoCode") Integer sidoCode, @Param("gugunCode") Integer gugunCode,
            @Param("contentTypeId") Integer contentTypeId, @Param("keyword") String keyword,
            @Param("minLat") Double minLat, @Param("maxLat") Double maxLat, @Param("minLng") Double minLng, @Param("maxLng") Double maxLng,
            @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM attractions a"
            + "<where>"
            + "<if test='sidoCode != null'>a.area_code = #{sidoCode}</if>"
            + "<if test='gugunCode != null'> AND a.si_gun_gu_code = #{gugunCode}</if>"
            + "<if test='contentTypeId != null'> AND a.content_type_id = #{contentTypeId}</if>"
            + "<if test=\"keyword != null and keyword != ''\"> AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.addr1 LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test='minLat != null'> AND a.latitude BETWEEN #{minLat} AND #{maxLat} AND a.longitude BETWEEN #{minLng} AND #{maxLng}</if>"
            + "</where></script>")
    int count(@Param("sidoCode") Integer sidoCode, @Param("gugunCode") Integer gugunCode,
            @Param("contentTypeId") Integer contentTypeId, @Param("keyword") String keyword,
            @Param("minLat") Double minLat, @Param("maxLat") Double maxLat, @Param("minLng") Double minLng, @Param("maxLng") Double maxLng);

    @Select("SELECT " + COLS + ", a.homepage, a.overview " + JOINS + "WHERE a.no = #{no}")
    Attraction findByNo(int no);

    /** 위경도 사각형 범위(인덱스 활용)로 후보를 줄인 뒤 서비스에서 정확한 거리를 계산한다. */
    @Select("<script>SELECT " + COLS + JOINS
            + "WHERE a.latitude BETWEEN #{minLat} AND #{maxLat} AND a.longitude BETWEEN #{minLng} AND #{maxLng}"
            + "<if test='types != null and types.size() > 0'> AND a.content_type_id IN "
            + "<foreach collection='types' item='t' open='(' separator=',' close=')'>#{t}</foreach></if>"
            + " LIMIT 500</script>")
    List<Attraction> findInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
            @Param("minLng") double minLng, @Param("maxLng") double maxLng, @Param("types") List<Integer> types);

    @Select("<script>SELECT " + COLS + JOINS + "WHERE a.no IN "
            + "<foreach collection='nos' item='n' open='(' separator=',' close=')'>#{n}</foreach></script>")
    List<Attraction> findByNos(@Param("nos") List<Integer> nos);

    /** 번호 목록의 사진과 소개 글 앞부분만 가져온다. (AI 가 장소를 고르고 설명할 때 근거로 쓴다) */
    @Select("<script>SELECT a.no, a.content_id, a.title, a.first_image1, LEFT(a.overview, 600) AS overview FROM attractions a WHERE a.no IN "
            + "<foreach collection='nos' item='n' open='(' separator=',' close=')'>#{n}</foreach></script>")
    List<Attraction> findBriefs(@Param("nos") List<Integer> nos);

    /** 사진이 있고 좌표가 정상인 장소 중에서 무작위로 뽑는다. (AI 추천 후보용) */
    @Select("<script>SELECT " + COLS + JOINS
            + "WHERE a.first_image1 IS NOT NULL AND a.first_image1 != '' AND a.latitude BETWEEN 33 AND 39 AND a.longitude BETWEEN 124 AND 132"
            + "<if test='sidoCode != null'> AND a.area_code = #{sidoCode}</if>"
            + "<if test='types != null and types.size() > 0'> AND a.content_type_id IN "
            + "<foreach collection='types' item='t' open='(' separator=',' close=')'>#{t}</foreach></if>"
            + "<if test=\"keyword != null and keyword != ''\"> AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.addr1 LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + " ORDER BY RAND() LIMIT #{size}</script>")
    List<Attraction> findRandom(@Param("sidoCode") Integer sidoCode, @Param("types") List<Integer> types,
            @Param("keyword") String keyword, @Param("size") int size);

    /** 이름 패턴(LIKE)에 맞는 후보들. 정확히 같은 이름, 그 이름으로 시작하는 이름, 사진 있는 곳, 짧은 이름 순 */
    @Select("<script>SELECT " + COLS + JOINS
            + "WHERE a.title LIKE #{pattern} AND a.latitude BETWEEN 33 AND 39 AND a.longitude BETWEEN 124 AND 132"
            + "<if test='sidoCode != null'> AND a.area_code = #{sidoCode}</if>"
            + " ORDER BY (a.title = #{name}) DESC, (a.title LIKE CONCAT(#{name}, '%')) DESC,"
            + " (a.first_image1 IS NULL OR a.first_image1 = ''), CHAR_LENGTH(a.title) LIMIT #{size}</script>")
    List<Attraction> findCandidatesByName(@Param("sidoCode") Integer sidoCode, @Param("name") String name,
            @Param("pattern") String pattern, @Param("size") int size);

    /** 이름이 포함된 장소 중 가장 잘 맞는 한 곳 (사진 있는 곳, 이름이 짧은 곳 우선) */
    @Select("<script>SELECT " + COLS + JOINS
            + "WHERE a.title LIKE CONCAT('%', #{name}, '%') AND a.latitude BETWEEN 33 AND 39 AND a.longitude BETWEEN 124 AND 132"
            + "<if test='sidoCode != null'> AND a.area_code = #{sidoCode}</if>"
            + " ORDER BY (a.title = #{name}) DESC, (a.title LIKE CONCAT(#{name}, '%')) DESC, (a.content_type_id IN (12, 14, 38, 39)) DESC,"
            + " (a.first_image1 IS NULL OR a.first_image1 = ''), CHAR_LENGTH(a.title) LIMIT 1</script>")
    Attraction findBestByName(@Param("sidoCode") Integer sidoCode, @Param("name") String name);

    @Update("UPDATE attractions SET overview = #{overview} WHERE no = #{no}")
    int updateOverview(@Param("no") int no, @Param("overview") String overview);

    @Update("UPDATE attractions SET first_image1 = #{url} WHERE no = #{no}")
    int updateImage(@Param("no") int no, @Param("url") String url);

    @Select("SELECT sido_code FROM sidos WHERE sido_name LIKE CONCAT('%', #{name}, '%') LIMIT 1")
    Integer findSidoCode(String name);
}
