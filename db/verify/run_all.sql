-- db/verify 폴더에서 실행한다. (source 경로는 mysql을 실행한 폴더 기준)
-- 00_base.sql은 build_base.py가 만든다. 15MB라 git에 올리지 않는다.
source 00_base.sql
source 10_schema.sql
source 20_tests.sql
source 30_spatial_compare.sql
