package com.mapmates.catalog.dao;

import com.mapmates.catalog.dto.PlaceWrite;
import java.sql.*;
import java.util.*;

public interface CatalogDao {
  List<Map<String, Object>> places(
      Connection c, String where, List<Object> args, int limit, int offset) throws SQLException;

  long count(Connection c, String where, List<Object> args) throws SQLException;

  Map<String, Object> place(Connection c, int id) throws SQLException;

  void ensure(Connection c, PlaceWrite p) throws SQLException;
}
