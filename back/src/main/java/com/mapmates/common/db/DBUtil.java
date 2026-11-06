package com.mapmates.common.db;

import com.mapmates.common.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** DB Connection 발급. 트랜잭션 관리는 {@link Tx}가 맡는다. */
public final class DBUtil {

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("MySQL JDBC 드라이버를 찾을 수 없습니다.");
        }
    }

    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                AppConfig.require("db.url"),
                AppConfig.require("db.user"),
                AppConfig.get("db.password", ""));
    }
}
