package com.ssafy.mapmates.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.common.AuthInterceptor;
import com.ssafy.mapmates.common.LoginUser;
import com.ssafy.mapmates.common.ViewDedup;
import com.ssafy.mapmates.dao.BoardDao;
import com.ssafy.mapmates.dto.Board;
import com.ssafy.mapmates.dto.Comment;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 공지사항(F109, 관리자만 작성) / 공유게시판(F110) CRUD + 댓글 */
@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {
    private final BoardDao boardDao;
    private final ViewDedup viewDedup;

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "FREE") String type,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.max(1, Math.min(size, 50));
        page = Math.max(1, page);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", boardDao.findAll(type, keyword, (page - 1) * size, size));
        result.put("total", boardDao.count(type, keyword));
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @GetMapping("/{id}")
    public Board detail(@PathVariable int id, HttpServletRequest req) {
        // 같은 사람이 다시 열거나 댓글을 쓴 뒤 새로 불러와도 조회수는 한 번만 오른다
        if (viewDedup.firstView("board", id, req)) boardDao.increaseHit(id);
        Board board = find(id);
        board.setComments(boardDao.findComments(id));
        return board;
    }

    @PostMapping
    public Board create(@RequestBody Board board, HttpServletRequest req) {
        LoginUser user = AuthInterceptor.require(req);
        validate(board);
        String type = "NOTICE".equals(board.getType()) ? "NOTICE" : "FREE";
        if ("NOTICE".equals(type) && !user.isAdmin()) throw ApiException.forbidden("공지사항은 관리자만 작성할 수 있습니다.");
        board.setType(type);
        board.setMemberId(user.id());
        boardDao.insert(board);
        return find(board.getId());
    }

    @PutMapping("/{id}")
    public Board update(@PathVariable int id, @RequestBody Board board, HttpServletRequest req) {
        owned(find(id), AuthInterceptor.require(req));
        validate(board);
        board.setId(id);
        boardDao.update(board);
        return find(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable int id, HttpServletRequest req) {
        owned(find(id), AuthInterceptor.require(req));
        boardDao.delete(id);
        return Map.of("message", "삭제되었습니다.");
    }

    @PostMapping("/{id}/comments")
    public Board addComment(@PathVariable int id, @RequestBody Comment comment, HttpServletRequest req) {
        LoginUser user = AuthInterceptor.require(req);
        find(id);
        if (comment.getContent() == null || comment.getContent().isBlank()) throw ApiException.badRequest("댓글 내용을 입력해 주세요.");
        comment.setBoardId(id);
        comment.setMemberId(user.id());
        boardDao.insertComment(comment);
        return detailWithoutHit(id);
    }

    @DeleteMapping("/comments/{commentId}")
    public Map<String, Object> deleteComment(@PathVariable int commentId, HttpServletRequest req) {
        LoginUser user = AuthInterceptor.require(req);
        Comment c = boardDao.findComment(commentId);
        if (c == null) throw ApiException.notFound("댓글을 찾을 수 없습니다.");
        if (c.getMemberId() != user.id() && !user.isAdmin()) throw ApiException.forbidden("작성자만 삭제할 수 있습니다.");
        boardDao.deleteComment(commentId);
        return Map.of("message", "삭제되었습니다.");
    }

    private Board detailWithoutHit(int id) {
        Board board = find(id);
        board.setComments(boardDao.findComments(id));
        return board;
    }

    private Board find(int id) {
        Board board = boardDao.findById(id);
        if (board == null) throw ApiException.notFound("게시글을 찾을 수 없습니다.");
        return board;
    }

    private void owned(Board board, LoginUser user) {
        if (board.getMemberId() != user.id() && !user.isAdmin()) throw ApiException.forbidden("작성자만 가능합니다.");
    }

    private void validate(Board board) {
        if (board.getTitle() == null || board.getTitle().isBlank() || board.getContent() == null || board.getContent().isBlank()) {
            throw ApiException.badRequest("제목과 내용을 입력해 주세요.");
        }
    }
}
