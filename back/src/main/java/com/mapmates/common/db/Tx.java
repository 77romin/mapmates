package com.mapmates.common.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Service 계층의 트랜잭션 템플릿.
 * <pre>
 * Plan saved = Tx.write(conn -> {
 *     long id = planDao.insert(conn, plan);
 *     planItemDao.insertAll(conn, id, items);
 *     return planDao.findById(conn, id);
 * });
 * </pre>
 * write는 작업 중 예외가 나면 전부 롤백한다. DAO는 받은 Connection을 닫지 않는다.
 */
public final class Tx {

    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection conn) throws SQLException;
    }

    private Tx() {
    }

    /** 조회 전용. auto-commit 상태로 실행한다. */
    public static <T> T read(SqlWork<T> work) {
        try (Connection conn = DBUtil.getConnection()) {
            conn.setReadOnly(true);
            return work.apply(conn);
        } catch (SQLException e) {
            throw new DataAccessException("DB 조회 중 오류가 발생했습니다.", e);
        }
    }

    /** 변경 작업. 하나의 트랜잭션으로 묶고 실패 시 롤백한다. */
    public static <T> T write(SqlWork<T> work) {
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("DB 변경 중 오류가 발생했습니다.", e);
        }
    }
}
