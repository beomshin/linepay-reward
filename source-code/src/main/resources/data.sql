-- =====================================================================
-- Seed Data (과제 10절)
--  - 일자(YYYYMMDD)와 시간(HHmmss)은 컬럼을 나눠 저장한다. (KST 기준)
--  - 미션 참여 이력과 보상 지급 이력은 없는 상태로 시작한다.
--  - 쿠폰 템플릿(10.4)은 외부 쿠폰 시스템 소유 데이터이므로
--    외부 시스템 재현체(FakeCouponSystem)에 적재한다.
-- =====================================================================

-- 10.1 User
INSERT INTO users (user_id, user_name) VALUES ('USER_0001', 'Brown');
INSERT INTO users (user_id, user_name) VALUES ('USER_0002', 'Cony');
INSERT INTO users (user_id, user_name) VALUES ('USER_0003', 'Moon');

-- 10.2 Mission (entry_start_at <= 현재 시각 < entry_end_at)
INSERT INTO mission (mission_id, mission_type, title, entry_start_date, entry_start_time, entry_end_date, entry_end_time)
VALUES ('MISSION_0001', 'VISIT_PAGE', '페이지 방문하고 포인트 받기', '20260101', '000000', '20260501', '000000');
INSERT INTO mission (mission_id, mission_type, title, entry_start_date, entry_start_time, entry_end_date, entry_end_time)
VALUES ('MISSION_0002', 'RANDOM_BOX', '랜덤 박스 열고 포인트 또는 쿠폰 받기', '20260501', '000000', '20270101', '000000');
INSERT INTO mission (mission_id, mission_type, title, entry_start_date, entry_start_time, entry_end_date, entry_end_time)
VALUES ('MISSION_0003', 'RANDOM_BOX', '랜덤 박스 열고 포인트 받기', '20260501', '000000', '20270101', '000000');

-- 10.3 Mission Item
INSERT INTO mission_item (mission_item_id, mission_id, item_type, coupon_template_id) VALUES ('ITEM_0001', 'MISSION_0001', 'REWARD_POINT', NULL);
INSERT INTO mission_item (mission_item_id, mission_id, item_type, coupon_template_id) VALUES ('ITEM_0002', 'MISSION_0002', 'REWARD_POINT', NULL);
INSERT INTO mission_item (mission_item_id, mission_id, item_type, coupon_template_id) VALUES ('ITEM_0003', 'MISSION_0002', 'COUPON', 'COUPON_TEMPLATE_0001');
INSERT INTO mission_item (mission_item_id, mission_id, item_type, coupon_template_id) VALUES ('ITEM_0004', 'MISSION_0003', 'REWARD_POINT', NULL);
