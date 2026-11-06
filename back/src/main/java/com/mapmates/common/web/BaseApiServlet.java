package com.mapmates.common.web;

import com.mapmates.common.exception.ApiException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 도메인 Controller의 부모 클래스.
 * <p>
 * 하위 클래스는 {@code @WebServlet("/api/posts/*")}처럼 등록하고 {@link #routes(Routes)}에서
 * 경로 패턴과 핸들러를 연결한다. 패턴은 서블릿 매핑 이후의 경로(pathInfo)이며 {@code {id}}로 변수를 받는다.
 * <pre>
 * r.get("/", this::list);
 * r.get("/{id}", this::detail);
 * r.post("/{id}/comments", this::writeComment);
 * </pre>
 * 핸들러가 객체를 반환하면 JSON으로, null을 반환하면 204로 응답한다.
 * ApiException은 해당 상태 코드의 JSON 오류로, 그 외 예외는 500으로 바꾼다.
 */
public abstract class BaseApiServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BaseApiServlet.class.getName());

    @FunctionalInterface
    public interface Handler {
        Object handle(ApiRequest request) throws Exception;
    }

    private record Route(String method, String[] segments, Handler handler) {
    }

    /** 라우트 등록기. */
    public static final class Routes {
        private final List<Route> list = new ArrayList<>();

        public void get(String pattern, Handler handler) {
            add("GET", pattern, handler);
        }

        public void post(String pattern, Handler handler) {
            add("POST", pattern, handler);
        }

        public void put(String pattern, Handler handler) {
            add("PUT", pattern, handler);
        }

        public void patch(String pattern, Handler handler) {
            add("PATCH", pattern, handler);
        }

        public void delete(String pattern, Handler handler) {
            add("DELETE", pattern, handler);
        }

        private void add(String method, String pattern, Handler handler) {
            list.add(new Route(method, split(pattern), handler));
        }
    }

    private final Routes routes = new Routes();

    protected void validateResource(ApiRequest request) {}

    /** 하위 클래스가 라우트를 등록한다. */
    protected abstract void routes(Routes r);

    @Override
    public void init() throws ServletException {
        super.init();
        routes(routes);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod().toUpperCase();
        String[] path = split(req.getPathInfo());
        try {
            boolean pathMatched = false;
            for (Route route : routes.list) {
                Map<String, String> vars = match(route.segments(), path);
                if (vars == null) {
                    continue;
                }
                pathMatched = true;
                if (route.method().equals(method)) {
                    ApiRequest request = new ApiRequest(req, resp, vars);
                    validateResource(request);
                    Object body = route.handler().handle(request);
                    writeSuccess(resp, request.status(), body);
                    return;
                }
            }
            if (pathMatched) {
                throw new ApiException(405, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식이에요.");
            }
            throw ApiException.notFound("요청한 API를 찾을 수 없어요.");
        } catch (ApiException e) {
            writeError(resp, e.getStatus(), e.getCode(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, method + " " + req.getRequestURI() + " 처리 중 오류", e);
            writeError(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했어요. 잠시 후 다시 시도해주세요.");
        }
    }

    private static void writeSuccess(HttpServletResponse resp, int status, Object body) throws IOException {
        if (body == null) {
            resp.setStatus(status == HttpServletResponse.SC_OK ? HttpServletResponse.SC_NO_CONTENT : status);
            return;
        }
        resp.setStatus(status);
        writeJson(resp, body);
    }

    private static void writeError(HttpServletResponse resp, int status, String code, String message) throws IOException {
        if (resp.isCommitted()) {
            return;
        }
        resp.resetBuffer();
        resp.setStatus(status);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        writeJson(resp, body);
    }

    private static void writeJson(HttpServletResponse resp, Object body) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        JsonUtil.write(resp.getWriter(), body);
    }

    private static String[] split(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return new String[0];
        }
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/+");
    }

    /** 패턴과 실제 경로가 맞으면 경로 변수 맵을, 아니면 null을 반환한다. */
    private static Map<String, String> match(String[] pattern, String[] path) {
        if (pattern.length != path.length) {
            return null;
        }
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                vars.put(p.substring(1, p.length() - 1), path[i]);
            } else if (!p.equals(path[i])) {
                return null;
            }
        }
        return vars;
    }
}
