package com.mapmates.common.db;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebListener;
@WebListener public class PoolLifecycle implements ServletContextListener {
 public void contextInitialized(ServletContextEvent event){if(com.mapmates.common.config.AppConfig.get("public.url","").startsWith("https://"))event.getServletContext().getSessionCookieConfig().setSecure(true);}
 public void contextDestroyed(ServletContextEvent event){com.mapmates.common.security.ResetMail.close();DBUtil.close();}
}
