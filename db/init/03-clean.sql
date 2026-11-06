SET NAMES utf8mb4;
USE mapmates;
UPDATE contenttypes SET content_type_name='음식점' WHERE content_type_id=39;
UPDATE attractions SET latitude=NULL,longitude=NULL WHERE NOT(latitude BETWEEN 33 AND 39 AND longitude BETWEEN 124 AND 132);
