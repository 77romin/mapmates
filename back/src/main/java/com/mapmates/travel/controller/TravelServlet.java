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
    if ((plans(q) && n > 1 && !(q.raw().getMethod().equals("GET")&&path.matches("/[0-9]+/revisions")))
        || (favorites(q) && (n > 1 || (n == 1 && !q.raw().getMethod().equals("DELETE")))))
      throw com.mapmates.common.exception.ApiException.notFound("요청한 API를 찾을 수 없어요.");
  }

  protected void routes(Routes r) {
    var approval=new com.mapmates.companion.service.ApprovalService();
    r.get("/{id}/requests",q->approval.requests(q.pathLong("id"),q.requireLogin().id()));
    r.get("/{id}/requests/me",q->approval.mine(q.pathLong("id"),q.requireLogin().id()));
    r.put("/{id}/requests/{member}",q->{approval.decide(q.pathLong("id"),q.pathLong("member"),q.requireLogin().id(),q.body(com.mapmates.companion.service.ApprovalService.Decision.class).status());return null;});
    r.put("/{id}/lifecycle",q->{new com.mapmates.companion.service.TripLifecycle().change(q.pathLong("id"),q.requireLogin().id(),q.body(com.mapmates.companion.service.TripLifecycle.Change.class).lifecycle());return service.companion(q.pathLong("id"));});
    r.get("/{id}/revisions",q->{if(!plans(q))throw com.mapmates.common.exception.ApiException.notFound("요청을 찾을 수 없어요.");service.plan(q.pathLong("id"),q.requireLogin().id());return com.mapmates.common.db.Tx.read(c->com.mapmates.common.db.Sql.rows(c,"SELECT id,summary,created_at AS createdAt FROM plan_revisions WHERE plan_id=? ORDER BY id DESC LIMIT 50",q.pathLong("id")));});
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
        q -> {approval.apply(q.pathLong("id"),q.requireLogin().id(),q.body(com.mapmates.companion.service.ApprovalService.Apply.class).introduction());return service.companion(q.pathLong("id"));});
    r.delete(
        "/{id}/participants/me",
        q -> {approval.cancel(q.pathLong("id"),q.requireLogin().id());return service.companion(q.pathLong("id"));});
    r.post(
        "/{id}/close",
        q ->
            service.edit(
                q.pathLong("id"),
                q.requireLogin().id(),
                new CompanionWrite(null, null, null, null, true)));
  }
}
