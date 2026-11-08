package com.mapmates.moderation;
import com.mapmates.common.web.*;import jakarta.servlet.annotation.WebServlet;
@WebServlet(urlPatterns={"/api/blocks/*","/api/reports/*"})
public class SafetyServlet extends BaseApiServlet {
 private final SafetyService service=new SafetyService();
 protected void validateResource(ApiRequest q){String path=q.raw().getPathInfo(),method=q.raw().getMethod();if(q.raw().getServletPath().equals("/api/blocks")&&!(method.equals("GET")&&(path==null||path.equals("/"))||java.util.Set.of("PUT","DELETE").contains(method)&&path!=null&&path.matches("/[0-9]+")))throw com.mapmates.common.exception.ApiException.notFound("요청을 찾을 수 없어요.");}
 protected void routes(Routes r){
  r.get("/",q->q.raw().getServletPath().equals("/api/blocks")?service.blocks(q.requireLogin().id()):service.list(q.requireLogin(),q.query("status"),q.queryInt("page",1,1,100000),q.queryInt("size",20,1,100)));
  r.get("/mine",q->service.mine(q.requireLogin().id()));
  r.post("/",q->{q.status(201);return service.report(q.requireLogin().id(),q.body(SafetyService.Report.class));});
  r.put("/{id}",q->{if(q.raw().getServletPath().equals("/api/blocks"))service.block(q.requireLogin().id(),q.pathLong("id"),true);else service.review(q.pathLong("id"),q.requireLogin(),q.body(SafetyService.Review.class));return null;});
  r.delete("/{id}",q->{if(!q.raw().getServletPath().equals("/api/blocks"))throw com.mapmates.common.exception.ApiException.notFound("요청을 찾을 수 없어요.");service.block(q.requireLogin().id(),q.pathLong("id"),false);return null;});
 }
}
