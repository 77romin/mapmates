package com.mapmates.member.dao;

import com.mapmates.member.dto.Member;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** members 테이블 접근. Connection은 Service(Tx)가 넘겨주며 DAO는 닫지 않는다. */
public interface MemberDao {

    long insert(Connection conn, Member member) throws SQLException;

    Optional<Member> findById(Connection conn, long id) throws SQLException;

    Optional<Member> findByEmail(Connection conn, String email) throws SQLException;

    boolean existsByEmail(Connection conn, String email) throws SQLException;

    /** excludeId가 null이 아니면 그 회원은 제외하고 중복을 검사한다(내 닉네임 유지 시). */
    boolean existsByNickname(Connection conn, String nickname, Long excludeId) throws SQLException;

    int updateProfile(Connection conn, Member member) throws SQLException;

    int updatePassword(Connection conn, long id, String passwordHash) throws SQLException;

    int delete(Connection conn, long id) throws SQLException;
}
