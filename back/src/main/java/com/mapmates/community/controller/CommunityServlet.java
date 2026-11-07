package com.mapmates.community.controller;

import com.mapmates.common.web.*;
import com.mapmates.community.dto.*;
import com.mapmates.community.service.*;
import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/api/posts/*", "/api/comments/*"})
public class CommunityServlet extends BaseApiServlet {
  private final CommunityService service = new CommunityServiceImpl();

  @Override
  protected void validateResource(ApiRequest q) {
    if (q.raw().getServletPath().equals("/api/comments")
        && !(q.raw().getPathInfo() != null
            && q.raw().getPathInfo().matches("/[0-9]+")
            && java.util.Set.of("PUT", "DELETE").contains(q.raw().getMethod())))
      throw com.mapmates.common.exception.ApiException.notFound("요청한 API를 찾을 수 없어요.");
  }

  protected void routes(Routes r) {
    r.get("/", q -> service.list(q));
    r.post(
        "/",
        q -> {
          q.status(201);
          return service.save(0, q.requireLogin(), q.body(PostWrite.class));
        });
    r.get("/{id}", q -> service.post(q.pathLong("id"), true));
    r.put(
        "/{id}",
        q ->
            q.raw().getServletPath().equals("/api/comments")
                ? service.editComment(
                    q.pathLong("id"), q.requireLogin(), q.body(CommentWrite.class))
                : service.save(q.pathLong("id"), q.requireLogin(), q.body(PostWrite.class)));
    r.delete(
        "/{id}",
        q -> {
          if (q.raw().getServletPath().equals("/api/comments"))
            service.deleteComment(q.pathLong("id"), q.requireLogin());
          else service.deletePost(q.pathLong("id"), q.requireLogin());
          return null;
        });
    r.get("/{id}/comments", q -> service.comments(false, q.pathLong("id")));
    r.post(
        "/{id}/comments",
        q -> {
          q.status(201);
          return service.addComment(
              false, q.pathLong("id"), q.requireLogin(), q.body(CommentWrite.class));
        });
  }
}
