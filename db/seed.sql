SET NAMES utf8mb4;
USE mapmates;
-- Local demonstration accounts; password: travel1234. Never use for production.
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(1,'traveler1@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자1','여행친구1','1998-01-01','female','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(2,'traveler2@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자2','여행친구2','1998-01-01','male','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(3,'traveler3@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자3','여행친구3','1998-01-01','female','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(4,'traveler4@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자4','여행친구4','1998-01-01','male','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(5,'traveler5@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자5','여행친구5','1998-01-01','female','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(6,'traveler6@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자6','여행친구6','1998-01-01','male','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(7,'traveler7@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자7','여행친구7','1998-01-01','female','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(8,'traveler8@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자8','여행친구8','1998-01-01','male','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(9,'traveler9@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자9','여행친구9','1998-01-01','female','member');
INSERT INTO members(id,email,password_hash,name,nickname,birth_date,gender,role) VALUES(10,'traveler10@mapmates.test','pbkdf2$210000$bWFwbWF0ZXMtZGVtby12MSE=$Nmn39vROOoUpEF4ivPa+IrO7i/Erimj6Edn22musErM=','여행자10','여행친구10','1998-01-01','male','admin');
INSERT INTO plans(id,owner_id,title,start_date,end_date,capacity,budget,region,theme,description) VALUES(1,1,'서울 산책 동행','2026-11-14','2026-11-14',4,30000,'서울','문화','서울의 관광지를 함께 걸어요.');
INSERT INTO plan_items(plan_id,content_id,day_no,visit_time,sort_order) SELECT 1,content_id,1,'10:00',ROW_NUMBER() OVER(ORDER BY content_id) FROM attractions WHERE area_code=1 AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY content_id LIMIT 3;
INSERT INTO companions(plan_id) VALUES(1);
INSERT INTO posts(owner_id,board,category,title,content) VALUES(10,'notice','안내','MapMates 이용 안내','여행계획을 저장하고 동행을 모집해보세요.'),(2,'travel','후기','서울 산책 추천','여유로운 서울 산책 코스를 함께 나눠요.');
INSERT INTO comments(owner_id,post_id,content) VALUES(1,2,'다음 여행에 참고할게요.');
