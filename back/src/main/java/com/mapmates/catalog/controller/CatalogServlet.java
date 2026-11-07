package com.mapmates.catalog.controller;

import com.mapmates.catalog.service.*;
import com.mapmates.common.db.*;
import com.mapmates.common.web.*;
import jakarta.servlet.annotation.WebServlet;

@WebServlet(
    urlPatterns = {
      "/api/attractions/*",
      "/api/areas/*",
      "/api/content-types",
      "/api/popular-places"
    })
public class CatalogServlet extends BaseApiServlet {
  private final CatalogService service = new CatalogServiceImpl();

  @Override
  protected void validateResource(ApiRequest q) {
    String resource = q.raw().getServletPath(), path = q.raw().getPathInfo();
    if ((resource.equals("/api/areas")
            && path != null
            && !path.equals("/")
            && !path.matches("/[0-9]+/guguns"))
        || (resource.equals("/api/attractions") && path != null && path.endsWith("/guguns")))
      throw com.mapmates.common.exception.ApiException.notFound("요청한 API를 찾을 수 없어요.");
  }

  protected void routes(Routes r) {
    r.get(
        "/",
        q -> {
          String path = q.raw().getServletPath();
          return switch (path) {
            case "/api/areas" ->
                Tx.read(
                    c ->
                        Sql.rows(
                            c,
                            "SELECT sido_code AS code,sido_name AS name FROM sidos ORDER BY"
                                + " sido_code"));
            case "/api/content-types" ->
                Tx.read(
                    c ->
                        Sql.rows(
                            c,
                            "SELECT content_type_id AS id,content_type_name AS name FROM"
                                + " contenttypes ORDER BY content_type_id"));
            case "/api/popular-places" -> service.popular(q);
            default -> service.list(q, false);
          };
        });
    r.get("/nearby", q -> service.list(q, true));
    r.get("/{id}", q -> service.detail((int) q.pathLong("id")));
    r.get(
        "/{id}/guguns",
        q ->
            Tx.read(
                c ->
                    Sql.rows(
                        c,
                        "SELECT gugun_code AS code,gugun_name AS name FROM guguns WHERE sido_code=?"
                            + " ORDER BY gugun_code",
                        q.pathLong("id"))));
  }
}
