package com.mapmates.catalog.service;

import com.mapmates.catalog.dao.*;
import com.mapmates.common.db.*;
import com.mapmates.common.exception.ApiException;
import com.mapmates.common.web.*;
import java.util.*;

public final class CatalogServiceImpl implements CatalogService {
  private final CatalogDao dao = new CatalogDaoImpl();

  public static double coordinate(String raw, double fallback, double min, double max) {
    if (raw == null) return fallback;
    try {
      double v = Double.parseDouble(raw);
      if (!Double.isFinite(v) || v < min || v > max) throw new NumberFormatException();
      return v;
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("좌표 또는 범위를 확인해주세요.");
    }
  }

  public Object list(ApiRequest r, boolean nearby) {
    var args = new ArrayList<Object>();
    String where = "1=1";
    for (String[] f :
        new String[][] {
          {"areaCode", "area_code"},
          {"sigunguCode", "si_gun_gu_code"},
          {"contentTypeId", "content_type_id"}
        })
      if (r.query(f[0]) != null) {
        int v = r.queryInt(f[0], 1, 1, 999);
        where += " AND " + f[1] + "=?";
        args.add(v);
      }
    if (r.query("keyword") != null) {
      where += " AND title LIKE ?";
      args.add("%" + Validator.requireText(r.query("keyword"), 100, "검색어") + "%");
    }
    if (nearby) {
      double lat = coordinate(r.query("lat"), 37.5665, -90, 90),
          lng = coordinate(r.query("lng"), 126.978, -180, 180),
          radius = coordinate(r.query("radius"), 5, 0.1, 50);
      where +=
          " AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? AND"
              + " ST_Distance_Sphere(POINT(longitude,latitude),POINT(?,?))<=?";
      Collections.addAll(
          args,
          lat - radius / 111,
          lat + radius / 111,
          lng - radius / (111 * Math.max(.01, Math.cos(Math.toRadians(lat)))),
          lng + radius / (111 * Math.max(.01, Math.cos(Math.toRadians(lat)))),
          lng,
          lat,
          radius * 1000);
    }
    int page = r.queryInt("page", 1, 1, 100000), size = r.queryInt("size", 20, 1, 100);
    String clause = where;
    return Tx.read(
        c ->
            Map.of(
                "items",
                dao.places(c, clause, args, size, (page - 1) * size),
                "page",
                page,
                "size",
                size,
                "total",
                dao.count(c, clause, args)));
  }

  public Object detail(int id) {
    return Tx.read(
        c -> {
          var p = dao.place(c, id);
          if (p == null) throw ApiException.notFound("관광지를 찾을 수 없어요.");
          return p;
        });
  }

  public Object popular(ApiRequest r) {
    double south = coordinate(r.query("south"), 33, -90, 90),
        north = coordinate(r.query("north"), 39, -90, 90),
        west = coordinate(r.query("west"), 124, -180, 180),
        east = coordinate(r.query("east"), 132, -180, 180);
    if (south > north || west > east) throw ApiException.badRequest("지도 범위를 확인해주세요.");
    return Tx.read(
        c ->
            Sql.rows(
                c,
                "SELECT a.content_id AS id,a.title,a.latitude AS lat,a.longitude AS"
                    + " lng,a.first_image1 AS image,a.addr1 AS address,a.content_type_id AS"
                    + " contentTypeId,COUNT(DISTINCT p.owner_id) AS userCount,COUNT(DISTINCT p.id)"
                    + " AS planCount FROM plan_items pi JOIN plans p ON p.id=pi.plan_id JOIN"
                    + " attractions a ON a.content_id=pi.content_id WHERE a.latitude BETWEEN ? AND"
                    + " ? AND a.longitude BETWEEN ? AND ? GROUP BY a.content_id ORDER BY userCount"
                    + " DESC,planCount DESC,a.title COLLATE utf8mb4_0900_ai_ci,a.content_id LIMIT"
                    + " 10",
                south,
                north,
                west,
                east));
  }
}
