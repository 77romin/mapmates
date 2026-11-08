package com.mapmates.common.web;
import com.mapmates.common.db.*;import com.mapmates.common.exception.ApiException;import jakarta.servlet.annotation.WebServlet;import java.util.*;
@WebServlet("/api/health") public class HealthServlet extends BaseApiServlet {
 protected void routes(Routes r){r.get("/",q->{try{return Tx.read(c->{if(Sql.number(Sql.one(c,"SELECT COUNT(*) n FROM schema_migrations WHERE version='106_moderation'"),"n")!=1)throw new ApiException(503,"NOT_READY","DB 업데이트가 필요해요.");return Map.of("status","ok");});}catch(com.mapmates.common.db.DataAccessException e){throw new ApiException(503,"NOT_READY","서버 준비 중이에요.");}});}
}
