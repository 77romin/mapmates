# 실행·갱신·배포 준비

현재 로컬에서 실행하며 실제 서버·도메인 배포나 인증서 발급은 하지 않았다.

## 로컬 실행과 기존 DB 갱신

새 환경은 `.env.example`을 `.env`로 복사하고 필요한 키를 설정한다.

```sh
docker compose up -d --build --wait
```

앱은 http://localhost:8080/mapmates/index.html, 개발 이메일함은 http://localhost:8026 이다. 복구 화면에서 이메일을 요청한 뒤 Mailpit에 도착한 링크로 비밀번호를 변경한다. 개발 메일은 실제 수신자에게 전달되지 않는다.

이미 초기화된 DB는 이미지 재빌드만으로 변경 SQL이 실행되지 않는다. 먼저 아래 명령을 실행한다. 서비스 데이터가 있는 볼륨을 삭제하지 않는다.

```sh
python3 scripts/migrate.py
docker compose up -d --build --wait web mail
```

`schema_migrations`에 없는 파일만 순서대로 반영한다. MySQL DDL은 암묵적으로 커밋되므로 실행 전 백업하고 실패 시 해당 파일과 DB 상태를 확인한다. 기준 `schema.sql`과 전체 `schema-current.sql`은 새 DB용이며 기존 DB에 재실행하지 않는다.

```sh
python3 scripts/schema_snapshot.py
python3 scripts/schema_snapshot.py --check
```

## 백업과 복구 검증

```sh
python3 scripts/backup.py --output /안전한/경로/mapmates-backup.sql
python3 scripts/restore.py /안전한/경로/mapmates-backup.sql --database mapmates_restore_check
```

백업은 단일 트랜잭션 덤프이며 새 파일을 권한 0600으로 생성하고 체크섬을 출력한다. 기존 파일은 덮어쓰지 않는다. 개인정보와 토큰 해시가 포함되므로 저장소에 커밋하지 않고 접근을 제한한 외부 저장소에 보관한다. 보존 주기와 정기 실행은 실제 운영 서버에 맞춰 설정한다.

복구는 기본적으로 새 `mapmates_restore_` 접두사 DB에만 수행한다. 같은 이름이 이미 있으면 중단한다. 테이블 수·주요 행 수·로그인을 확인한 뒤 검증 DB를 정리한다. 서비스 DB 대체에는 별도 `--database mapmates --replace-service-data`가 필요하며 실제 운영 복구 전에는 쓰기를 중지하고 최신 백업 및 분리 복구 검증을 완료한다.

## HTTPS 운영 구성

실제 도메인의 DNS, 80/443 포트가 열린 서버, Docker Compose의 `!reset`/`!override` 지원 버전과 인증된 SMTP가 필요하다. `.env`에 강한 DB 비밀번호, `MAPMATES_DOMAIN`, `MAPMATES_SMTP_HOST/PORT/USER/PASSWORD/FROM`을 설정한다. `.env`는 커밋하지 않는다.

```sh
export COMPOSE_PROJECT_NAME=mapmates-prod
export COMPOSE_FILE=compose.yaml:compose.production.yaml
docker compose up -d --build --wait
```

새 운영 프로젝트·볼륨에서 시작한다. 운영 구성은 시연 회원을 넣지 않고 Mailpit을 제외한다. Caddy가 HTTPS 인증서를 발급하고 Tomcat은 내부 네트워크에서만 접근한다. 인증서 볼륨도 보존한다. 카카오 JavaScript 키에 실제 HTTPS 도메인을 등록한다. 가입한 운영 담당자만 DB에서 관리자 역할로 승격한다.

백업·복구·마이그레이션 스크립트도 같은 `COMPOSE_PROJECT_NAME`, `COMPOSE_FILE` 환경에서 실행해야 정확한 프로젝트를 대상으로 한다. `/mapmates/api/health`는 DB 연결과 최신 변경 적용을 검사한다. 도메인이 없는 현재 단계에서는 운영 Compose 구성을 더미 값으로 검증했다.

## CI

GitHub는 main 푸시·PR·수동 실행에서 새 DB와 WAR, JUnit, API·추가 기능·알고리즘·Chrome 브라우저, 백업·분리 복구를 검사한다. GitLab은 기본 브랜치·MR에서 브라우저 외 동일한 서버 검증을 실행한다.

GitLab Runner는 Docker executor의 privileged Docker-in-Docker 실행과 `/certs/client` 공유 볼륨이 필요하다. DinD 연결은 TLS 2376을 사용한다. Runner 설정은 저장소 YAML만으로 적용되지 않으므로 과제 서버의 Runner 관리자 설정이 필요할 수 있다. 실제 파이프라인 성공 여부는 각 서비스 실행 결과로 확인한다.

```sh
python3 scripts/check_config.py
python3 scripts/test_api.py
python3 scripts/test_features.py
node front/tests/algorithms.cjs
```

브라우저 테스트는 `PLAYWRIGHT_MODULE`로 설치된 Playwright 모듈 경로를 지정하고 Chrome을 사용한다. 기능 테스트는 개발 Mailpit과 시연 관리자 계정을 사용하므로 운영 데이터에 실행하지 않는다. 요청 제한은 단일 JVM 기준이다. 여러 웹 인스턴스로 확장할 때는 세션·요청 제한 공유 저장소와 운영 로그·백업 보존 정책을 추가로 구성한다.


## GitLab 실행 상태 확인 — 2026-11-08

[파이프라인 #278164](https://lab.ssafy.com/s16/a11/project/6.database/3-kkm-mye/-/pipelines/278164)의 [verify 작업 #876093](https://lab.ssafy.com/s16/a11/project/6.database/3-kkm-mye/-/jobs/876093)은 `pending / stuck` 상태였다. 작업 화면에 활성 Runner가 없다는 안내가 표시됐다. 설정에서 이 프로젝트의 연결된 Runner 0개, 그룹 Runner 0개, 인스턴스 Runner 0개를 확인했다.

다른 프로젝트의 Runner를 임의 연결하거나 새 상주 Runner를 설치하지 않았다. 현재 GitLab은 코드 제출·동기화 용도로 유지하며 검증은 로컬과 GitHub Actions에서 실행한다. GitLab에서 직접 실행하려면 담당자가 이 프로젝트용 Docker Runner를 연결해야 한다. YAML이 통과했다는 것과 실제 작업 실행 성공은 구분한다. 공개 배포는 진행하지 않는다.
