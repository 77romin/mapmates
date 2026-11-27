-- 너랑 갈.지도 (mapmates) DB Schema
CREATE DATABASE IF NOT EXISTS mapmates DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE mapmates;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS comments, boards, news, hotplaces, mate_applications, mate_posts,
    plan_members, plan_items, plans, attractions, guguns, sidos, contenttypes, members;
SET FOREIGN_KEY_CHECKS = 1;

-- 회원
CREATE TABLE members (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(30)  NOT NULL UNIQUE COMMENT '로그인 아이디',
    password    VARCHAR(200) NOT NULL COMMENT 'PBKDF2 해시',
    name        VARCHAR(30)  NOT NULL,
    email       VARCHAR(100) NOT NULL,
    role        VARCHAR(10)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    tags        VARCHAR(200) NULL COMMENT '여행 취향 태그(쉼표 구분)',
    home_lat    DECIMAL(10,7) NULL COMMENT '출발지 위도',
    home_lng    DECIMAL(10,7) NULL COMMENT '출발지 경도',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='회원';

-- 지역/콘텐츠 코드
CREATE TABLE sidos (
    sido_code INT PRIMARY KEY,
    sido_name VARCHAR(20) NOT NULL
) COMMENT='시도';

CREATE TABLE guguns (
    sido_code  INT NOT NULL,
    gugun_code INT NOT NULL,
    gugun_name VARCHAR(20) NOT NULL,
    PRIMARY KEY (sido_code, gugun_code),
    CONSTRAINT fk_gugun_sido FOREIGN KEY (sido_code) REFERENCES sidos (sido_code)
) COMMENT='구군';

CREATE TABLE contenttypes (
    content_type_id   INT PRIMARY KEY,
    content_type_name VARCHAR(45) NOT NULL
) COMMENT='콘텐츠 타입';

-- 관광지 (한국관광공사 국문 관광정보)
CREATE TABLE attractions (
    no              INT AUTO_INCREMENT PRIMARY KEY,
    content_id      INT NULL,
    title           VARCHAR(500) NOT NULL,
    content_type_id INT NULL,
    area_code       INT NULL,
    si_gun_gu_code  INT NULL,
    first_image1    VARCHAR(200) NULL,
    first_image2    VARCHAR(200) NULL,
    map_level       INT NULL,
    latitude        DECIMAL(20,17) NULL,
    longitude       DECIMAL(20,17) NULL,
    tel             VARCHAR(50) NULL,
    addr1           VARCHAR(200) NULL,
    addr2           VARCHAR(100) NULL,
    homepage        VARCHAR(1000) NULL,
    overview        VARCHAR(10000) NULL,
    INDEX idx_attr_area_type (area_code, si_gun_gu_code, content_type_id),
    INDEX idx_attr_type (content_type_id),
    INDEX idx_attr_latlng (latitude, longitude),
    INDEX idx_attr_title (title(50)),
    CONSTRAINT fk_attr_type FOREIGN KEY (content_type_id) REFERENCES contenttypes (content_type_id),
    CONSTRAINT fk_attr_sido FOREIGN KEY (area_code) REFERENCES sidos (sido_code)
) COMMENT='관광지';

-- 여행 계획
CREATE TABLE plans (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    member_id  INT NOT NULL COMMENT '작성자',
    title      VARCHAR(100) NOT NULL,
    start_date DATE NULL,
    end_date   DATE NULL,
    budget     INT NOT NULL DEFAULT 0 COMMENT '예산(원)',
    memo       VARCHAR(1000) NULL,
    is_public  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '공유 여부',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_member (member_id),
    CONSTRAINT fk_plan_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) COMMENT='여행 계획';

CREATE TABLE plan_items (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    plan_id       INT NOT NULL,
    day_no        INT NOT NULL DEFAULT 1 COMMENT '일차',
    seq           INT NOT NULL DEFAULT 0 COMMENT '방문 순서',
    attraction_no INT NOT NULL,
    memo          VARCHAR(500) NULL,
    cost          INT NOT NULL DEFAULT 0 COMMENT '경비(원)',
    INDEX idx_item_plan (plan_id, day_no, seq),
    CONSTRAINT fk_item_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_attr FOREIGN KEY (attraction_no) REFERENCES attractions (no)
) COMMENT='여행 계획 방문지';

CREATE TABLE plan_members (
    plan_id   INT NOT NULL,
    member_id INT NOT NULL,
    role      VARCHAR(10) NOT NULL DEFAULT 'MATE' COMMENT 'OWNER / MATE',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_id, member_id),
    CONSTRAINT fk_pm_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) COMMENT='계획 참여자(메이트)';

-- 메이트 모집
CREATE TABLE mate_posts (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    plan_id    INT NOT NULL,
    member_id  INT NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    VARCHAR(2000) NULL,
    capacity   INT NOT NULL DEFAULT 4,
    tags       VARCHAR(200) NULL COMMENT '취향 태그(쉼표 구분)',
    status     VARCHAR(10) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / CLOSED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mate_status (status, created_at),
    CONSTRAINT fk_mate_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_mate_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) COMMENT='메이트 모집글';

CREATE TABLE mate_applications (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    post_id    INT NOT NULL,
    member_id  INT NOT NULL,
    message    VARCHAR(500) NULL,
    status     VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACCEPTED / REJECTED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_apply (post_id, member_id),
    CONSTRAINT fk_apply_post FOREIGN KEY (post_id) REFERENCES mate_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_apply_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) COMMENT='메이트 참여 신청';

-- 핫플레이스
CREATE TABLE hotplaces (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    member_id    INT NOT NULL,
    title        VARCHAR(100) NOT NULL,
    description  VARCHAR(2000) NULL,
    place_type   VARCHAR(30) NULL COMMENT '장소 유형',
    visited_date DATE NULL,
    latitude     DECIMAL(10,7) NOT NULL,
    longitude    DECIMAL(10,7) NOT NULL,
    image_path   VARCHAR(300) NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hot_latlng (latitude, longitude),
    CONSTRAINT fk_hot_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) COMMENT='핫플레이스';

-- 게시판(공지/자유)
CREATE TABLE boards (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    type       VARCHAR(10) NOT NULL DEFAULT 'FREE' COMMENT 'NOTICE / FREE',
    member_id  INT NOT NULL,
    title      VARCHAR(200) NOT NULL,
    content    TEXT NOT NULL,
    hit        INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_board_type (type, id),
    CONSTRAINT fk_board_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) COMMENT='게시판';

CREATE TABLE comments (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    board_id   INT NOT NULL,
    member_id  INT NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cmt_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT fk_cmt_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) COMMENT='댓글';

-- 여행 뉴스(크롤링)
CREATE TABLE news (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    keyword     VARCHAR(50) NOT NULL,
    title       VARCHAR(500) NOT NULL,
    link        VARCHAR(700) NOT NULL,
    source      VARCHAR(100) NULL,
    pub_date    VARCHAR(50) NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_news_link (link(255)),
    INDEX idx_news_keyword (keyword, id)
) COMMENT='여행 뉴스';
