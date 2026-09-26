-- =====================================================================
-- 비즈니스 키 채번용 시퀀스 (교정 3)
--  - 테이블은 JPA(ddl-auto)가 만들고, 이 스크립트는 그 뒤에 실행된다.
--  - DB 시퀀스는 동시 요청에서도 같은 값을 두 번 주지 않으므로 이력번호·리워드번호가 중복되지 않는다.
--  - 시퀀스 값은 트랜잭션이 롤백돼도 되돌아가지 않는다. (번호에 빈 값이 생길 수 있음)
-- =====================================================================
CREATE SEQUENCE IF NOT EXISTS participation_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS reward_no_seq START WITH 1 INCREMENT BY 1;
