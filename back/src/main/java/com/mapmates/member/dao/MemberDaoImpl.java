package com.mapmates.member.dao;

import com.mapmates.member.dto.Member;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

public class MemberDaoImpl implements MemberDao {

    private static final MemberDao INSTANCE = new MemberDaoImpl();

    private static final String COLUMNS =
            "id, email, password_hash, name, nickname, birth_date, gender, photo, bio, role, created_at, updated_at";

    private MemberDaoImpl() {
    }

    public static MemberDao getInstance() {
        return INSTANCE;
    }

    @Override
    public long insert(Connection conn, Member m) throws SQLException {
        String sql = "INSERT INTO members (email, password_hash, name, nickname, birth_date, gender, photo, bio) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getEmail());
            ps.setString(2, m.getPasswordHash());
            ps.setString(3, m.getName());
            ps.setString(4, m.getNickname());
            ps.setDate(5, Date.valueOf(m.getBirthDate()));
            ps.setString(6, m.getGender());
            ps.setString(7, m.getPhoto());
            ps.setString(8, m.getBio());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("회원 ID를 생성하지 못했습니다.");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<Member> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + COLUMNS + " FROM members WHERE id = ?")) {
            ps.setLong(1, id);
            return single(ps);
        }
    }

    @Override
    public Optional<Member> findByEmail(Connection conn, String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + COLUMNS + " FROM members WHERE email = ?")) {
            ps.setString(1, email);
            return single(ps);
        }
    }

    @Override
    public boolean existsByEmail(Connection conn, String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM members WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean existsByNickname(Connection conn, String nickname, Long excludeId) throws SQLException {
        String sql = "SELECT 1 FROM members WHERE nickname = ?" + (excludeId == null ? "" : " AND id <> ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nickname);
            if (excludeId != null) {
                ps.setLong(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public int updateProfile(Connection conn, Member m) throws SQLException {
        String sql = "UPDATE members SET name = ?, nickname = ?, gender = ?, photo = ?, bio = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getName());
            ps.setString(2, m.getNickname());
            ps.setString(3, m.getGender());
            ps.setString(4, m.getPhoto());
            ps.setString(5, m.getBio());
            ps.setLong(6, m.getId());
            return ps.executeUpdate();
        }
    }

    @Override
    public int updatePassword(Connection conn, long id, String passwordHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE members SET password_hash = ? WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    @Override
    public int delete(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM members WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }

    private Optional<Member> single(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    private Member map(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setId(rs.getLong("id"));
        m.setEmail(rs.getString("email"));
        m.setPasswordHash(rs.getString("password_hash"));
        m.setName(rs.getString("name"));
        m.setNickname(rs.getString("nickname"));
        Date birth = rs.getDate("birth_date");
        m.setBirthDate(birth == null ? null : birth.toLocalDate());
        m.setGender(rs.getString("gender"));
        m.setPhoto(rs.getString("photo"));
        m.setBio(rs.getString("bio"));
        m.setRole(rs.getString("role"));
        Timestamp created = rs.getTimestamp("created_at");
        m.setCreatedAt(created == null ? null : created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        m.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        return m;
    }
}
