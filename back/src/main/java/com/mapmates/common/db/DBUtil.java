package com.mapmates.common.db;
import com.mapmates.common.config.AppConfig;import com.zaxxer.hikari.*;import java.sql.*;
/** A bounded application pool; Connection.close returns a connection to this pool. */
public final class DBUtil {
 private static volatile HikariDataSource pool;
 private DBUtil(){}
 private static HikariDataSource source(){if(pool==null)synchronized(DBUtil.class){if(pool==null){var config=new HikariConfig();config.setDriverClassName("com.mysql.cj.jdbc.Driver");config.setJdbcUrl(AppConfig.require("db.url"));config.setUsername(AppConfig.require("db.user"));config.setPassword(AppConfig.get("db.password",""));config.setMaximumPoolSize(10);config.setMinimumIdle(1);config.setConnectionTimeout(5000);config.setValidationTimeout(2000);config.setIdleTimeout(60000);config.setMaxLifetime(600000);config.setPoolName("MapMates");pool=new HikariDataSource(config);}}return pool;}
 public static Connection getConnection()throws SQLException{return source().getConnection();}
 public static synchronized void close(){if(pool!=null){pool.close();pool=null;}}
}
