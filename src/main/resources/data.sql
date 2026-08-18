-- 파트너 정체성 시드 — 코드의 파트너 구현 3종(PartnerKey 선언)과 1:1로 일치해야 한다.
-- 불일치하면 PartnerRegistryReconciler가 기동을 실패시킨다 (분산 enum의 기동 대사).
insert into partners (name, partner_key) values ('CJ푸드빌', 'CJ_FOODVILLE');
insert into partners (name, partner_key) values ('롯데GRS', 'LOTTE_GRS');
insert into partners (name, partner_key) values ('버거킹', 'BURGER_KING');

-- 데모 매장 — 뚜레쥬르 직연동 매장 1곳 (stores → partner_stores → partners 조립 경로 확인용)
insert into stores (name, partner_type) values ('뚜레쥬르 역삼점', 'INTEGRATED_PARTNER');
insert into partner_stores (store_id, partner_id, partner_store_code)
select s.id, p.id, 'CJ-STORE-042' from stores s, partners p
where s.name = '뚜레쥬르 역삼점' and p.partner_key = 'CJ_FOODVILLE';
