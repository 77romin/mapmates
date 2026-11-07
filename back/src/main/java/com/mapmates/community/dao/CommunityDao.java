package com.mapmates.community.dao;

import java.sql.*;
import java.util.*;

public interface CommunityDao {
  Map<String, Object> post(Connection c, long id, boolean lock) throws SQLException;

  List<Map<String, Object>> comments(Connection c, String column, long id) throws SQLException;
}
