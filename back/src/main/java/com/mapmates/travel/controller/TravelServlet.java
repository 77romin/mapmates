package com.mapmates.travel.controller;

import com.mapmates.catalog.dto.PlaceWrite;
import com.mapmates.common.web.*;
import com.mapmates.travel.dto.*;
import com.mapmates.travel.service.*;
import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/api/plans/*", "/api/companions/*", "/api/favorites/*"})
public class TravelServlet extends BaseApiServlet {
  private final TravelService service = new TravelServiceImpl();

  private boolean plans(ApiRequest q) {
    return q.raw().getServletPath().equals("/api/plans");
  }

  private boolean favorites(ApiRequest q) {
    return q.raw().getServletPath().equals("/api/favorites");
  }

  @Override
  protected void validateResource(ApiRequest q) {
    String path = q.raw().getPathInfo();
    int n =
        path == null
            ? 0
            : (int) java.util.Arrays.stream(path.split("/")).filter(v -> !v.isBlank()).count();
    if ((plans(q) && n > 1)
        || (favorites(q) && (n > 1 || (n == 1 && !q.raw().getMethod().equals("DELETE")))))
      throw com.mapmates.common.exception.ApiException.notFound("요청한 API를 찾을 수 없어요.");
  }

  protected void routes(Routes r) {
    var comments = new com.mapmates.community.service.CommunityServiceImpl();
    r.get("/{id}/comments", q -> comments.comments(true, q.pathLong("id")));
    r.post(
        "/{id}/comments",
        q -> {
          q.status(201);
          return comments.addComment(
              true,
              q.pathLong("id"),
              q.requireLogin(),
              q.body(com.mapmates.community.dto.CommentWrite.class));
        });
    r.get(
        "/",
        q ->
            plans(q)
                ? service.plans(q.requireLogin().id())
                : favorites(q) ? service.favorites(q.requireLogin().id()) : service.companions(q));
    r.post(
        "/",
        q -> {
          long u = q.requireLogin().id();
          q.status(201);
          if (favorites(q)) {
            service.favorite(u, q.body(PlaceWrite.class));
            return null;
          }
          return plans(q)
              ? service.save(0, u, q.body(PlanWrite.class))
              : service.publish(u, q.body(CompanionWrite.class));
        });
    r.get(
        "/{id}",
        q ->
            plans(q)
                ? service.plan(q.pathLong("id"), q.requireLogin().id())
                : service.companion(q.pathLong("id")));
    r.put(
        "/{id}",
        q ->
            plans(q)
                ? service.save(q.pathLong("id"), q.requireLogin().id(), q.body(PlanWrite.class))
                : service.edit(
                    q.pathLong("id"), q.requireLogin().id(), q.body(CompanionWrite.class)));
    r.delete(
        "/{id}",
        q -> {
          long u = q.requireLogin().id(), id = q.pathLong("id");
          if (plans(q)) service.deletePlan(id, u);
          else if (favorites(q)) service.unfavorite(u, (int) id);
          else service.deleteCompanion(id, u);
          return null;
        });
    r.post(
        "/{id}/participants",
        q -> service.participate(q.pathLong("id"), q.requireLogin().id(), true));
    r.delete(
        "/{id}/participants/me",
        q -> service.participate(q.pathLong("id"), q.requireLogin().id(), false));
    r.post(
        "/{id}/close",
        q ->
            service.edit(
                q.pathLong("id"),
                q.requireLogin().id(),
                new CompanionWrite(null, null, null, null, true)));
  }
}
