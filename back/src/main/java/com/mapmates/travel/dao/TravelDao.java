package com.mapmates.travel.dao;

import com.mapmates.travel.dto.PlanWrite;
import java.sql.*;
import java.util.*;

public interface TravelDao {
  Map<String, Object> plan(Connection c, long id, boolean lock) throws SQLException;

  List<Map<String, Object>> plans(Connection c, long owner) throws SQLException;

  long save(Connection c, long id, long owner, PlanWrite p) throws SQLException;

  List<Map<String, Object>> items(Connection c, long id) throws SQLException;

  Map<String, Object> companion(Connection c, long id) throws SQLException;

  List<Map<String, Object>> participants(Connection c, long id) throws SQLException;
}
