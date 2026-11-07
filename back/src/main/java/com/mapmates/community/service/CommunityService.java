package com.mapmates.community.service;

import com.mapmates.common.security.SessionUser;
import com.mapmates.common.web.ApiRequest;
import com.mapmates.community.dto.*;

public interface CommunityService {
  Object list(ApiRequest r);

  Object post(long id, boolean view);

  Object save(long id, SessionUser user, PostWrite p);

  void deletePost(long id, SessionUser user);

  Object comments(boolean companion, long id);

  Object addComment(boolean companion, long id, SessionUser user, CommentWrite p);

  Object editComment(long id, SessionUser user, CommentWrite p);

  void deleteComment(long id, SessionUser user);
}
