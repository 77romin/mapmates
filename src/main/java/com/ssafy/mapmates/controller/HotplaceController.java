package com.ssafy.mapmates.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.common.AuthInterceptor;
import com.ssafy.mapmates.common.LoginUser;
import com.ssafy.mapmates.dao.HotplaceDao;
import com.ssafy.mapmates.dto.Hotplace;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 핫플레이스 등록(F105): 사진 + 지도 좌표 + 방문일 + 장소 유형 + 설명 */
@RestController
@RequestMapping("/api/hotplaces")
@RequiredArgsConstructor
public class HotplaceController {
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private final HotplaceDao hotplaceDao;

    @Value("${mapmates.upload-dir}")
    private String uploadDir;

    @GetMapping
    public List<Hotplace> list() {
        return hotplaceDao.findAll();
    }

    @PostMapping
    public Hotplace create(@RequestParam String title, @RequestParam(required = false) String description,
            @RequestParam(required = false) String placeType, @RequestParam(required = false) String visitedDate,
            @RequestParam double latitude, @RequestParam double longitude,
            @RequestParam(required = false) MultipartFile image, HttpServletRequest req) throws IOException {
        LoginUser user = AuthInterceptor.require(req);
        if (title.isBlank()) throw ApiException.badRequest("제목을 입력해 주세요.");
        Hotplace h = new Hotplace();
        h.setMemberId(user.id());
        h.setTitle(title);
        h.setDescription(description);
        h.setPlaceType(placeType);
        h.setVisitedDate(visitedDate == null || visitedDate.isBlank() ? null : visitedDate);
        h.setLatitude(latitude);
        h.setLongitude(longitude);
        if (image != null && !image.isEmpty()) h.setImagePath(saveImage(image));
        hotplaceDao.insert(h);
        return hotplaceDao.findById(h.getId());
    }

    @PutMapping("/{id}")
    public Hotplace update(@PathVariable int id, @RequestBody Hotplace body, HttpServletRequest req) {
        owned(id, AuthInterceptor.require(req));
        body.setId(id);
        if (body.getVisitedDate() != null && body.getVisitedDate().isBlank()) body.setVisitedDate(null);
        hotplaceDao.update(body);
        return hotplaceDao.findById(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable int id, HttpServletRequest req) {
        owned(id, AuthInterceptor.require(req));
        hotplaceDao.delete(id);
        return Map.of("message", "삭제되었습니다.");
    }

    private void owned(int id, LoginUser user) {
        Hotplace h = hotplaceDao.findById(id);
        if (h == null) throw ApiException.notFound("핫플레이스를 찾을 수 없습니다.");
        if (h.getMemberId() != user.id() && !user.isAdmin()) throw ApiException.forbidden("작성자만 가능합니다.");
    }

    /** 확장자를 검증하고 서버가 만든 파일명으로 저장한다. */
    private String saveImage(MultipartFile image) throws IOException {
        String name = image.getOriginalFilename() == null ? "" : image.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!IMAGE_EXT.contains(ext)) throw ApiException.badRequest("이미지 파일(jpg, png, gif, webp)만 올릴 수 있습니다.");
        Path dir = Path.of(uploadDir).toAbsolutePath();
        Files.createDirectories(dir);
        String saved = UUID.randomUUID() + "." + ext;
        image.transferTo(dir.resolve(saved));
        return "/uploads/" + saved;
    }
}
