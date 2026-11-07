package com.mapmates.travel.service;

import com.mapmates.catalog.dto.PlaceWrite;
import com.mapmates.common.web.ApiRequest;
import com.mapmates.travel.dto.*;

public interface TravelService {
  Object plans(long owner);

  Object plan(long id, long user);

  Object save(long id, long user, PlanWrite p);

  void deletePlan(long id, long user);

  Object companions(ApiRequest r);

  Object companion(long id);

  Object publish(long user, CompanionWrite p);

  Object edit(long id, long user, CompanionWrite p);

  void deleteCompanion(long id, long user);

  Object participate(long id, long user, boolean join);

  Object favorites(long user);

  void favorite(long user, PlaceWrite p);

  void unfavorite(long user, int id);
}
