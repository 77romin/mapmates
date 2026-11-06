-- 회원 기능 개발·테스트용 임시 DDL.
-- 정식 스키마는 DB PJT에서 ERD를 확정한 뒤 db/ 폴더에 작성하고, 이 파일은 그때 삭제한다.

CREATE DATABASE IF NOT EXISTS mapmates DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mapmates;

CREATE TABLE IF NOT EXISTS members (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  name          VARCHAR(30)  NOT NULL,
  nickname      VARCHAR(20)  NOT NULL,
  birth_date    DATE         NOT NULL,
  gender        ENUM('male', 'female') NOT NULL,
  photo         MEDIUMTEXT   NULL COMMENT '프로필 사진 URL 또는 data URL(320px JPEG)',
  bio           VARCHAR(200) NULL,
  role          ENUM('member', 'admin') NOT NULL DEFAULT 'member',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_members_email (email),
  UNIQUE KEY uk_members_nickname (nickname)
) ENGINE = InnoDB;
