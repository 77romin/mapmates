package com.mapmates.common.db;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebListener;
@WebListener public class PoolLifecycle implements ServletContextListener {
 public void contextDestroyed(ServletContextEvent event){DBUtil.close();}
}
