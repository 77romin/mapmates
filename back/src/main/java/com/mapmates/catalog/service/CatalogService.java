package com.mapmates.catalog.service;

import com.mapmates.common.web.ApiRequest;
import java.util.*;

public interface CatalogService {
  Object list(ApiRequest r, boolean nearby);

  Object detail(int id);

  Object popular(ApiRequest r);
}
