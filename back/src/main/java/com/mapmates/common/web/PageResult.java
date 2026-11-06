package com.mapmates.common.web;

import java.util.List;

/** 목록 응답 형식: {"items": [...], "page": 1, "size": 20, "total": 134} */
public record PageResult<T>(List<T> items, int page, int size, long total) {
}
