package com.mapmates.community.dto;

public record CommentWrite(String content, Long parentId, Long replyToMemberId) {}
