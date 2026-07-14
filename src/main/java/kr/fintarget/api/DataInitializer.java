package kr.fintarget.api;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final PolicyRepository policyRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (policyRepository.count() > 0) return;

        policyRepository.save(createPolicy("청년도약계좌", "만기 시 최대 5000만원 목돈 마련 가능한 청년 전용 저축 계좌", 19, 34, 7500000L, 2400000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("청년희망적금", "2년 만기 시 시중 이자 외 저축장려금 추가 지원", 19, 34, 3600000L, 360000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("청년내일저축계좌", "3년간 매월 10만원 저축 시 정부 지원금 추가 적립", 19, 34, 2400000L, 1080000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("청년형소득공제장기펀드", "3~5년 장기 투자 시 소득공제 혜택 제공", 19, 34, 5000000L, 2000000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("ISA 청년 우대형", "비과세 혜택 강화된 청년 전용 개인종합자산관리계좌", 19, 34, 5000000L, 500000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("청년 전세자금 대출", "전세 보증금 최대 2억원까지 저금리 대출 지원", 19, 34, 5000000L, 0L, "LOAN", "전국"));
        policyRepository.save(createPolicy("청년 월세 지원", "월 최대 20만원 월세 지원, 최대 12개월", 19, 34, 2000000L, 240000L, "HOUSING", "전국"));
        policyRepository.save(createPolicy("청년 매입임대주택", "시세 대비 저렴한 임대주택 우선 공급", 19, 39, 3000000L, 1200000L, "HOUSING", "전국"));
        policyRepository.save(createPolicy("행복주택 청년", "대중교통 인접 지역 저렴한 공공임대주택 공급", 19, 39, 4000000L, 2400000L, "HOUSING", "전국"));
        policyRepository.save(createPolicy("청년 버팀목 전세자금", "전세 보증금 최대 1억원 저금리 대출", 19, 34, 3500000L, 0L, "LOAN", "전국"));
        policyRepository.save(createPolicy("중소기업 취업 청년 전세 대출", "중소기업 재직 청년 전세자금 저금리 지원", 19, 34, 3500000L, 0L, "LOAN", "전국"));
        policyRepository.save(createPolicy("청년 주택드림 청약통장", "청약 당첨 시 분양가 80% 저금리 대출 연계", 19, 34, 6000000L, 0L, "HOUSING", "전국"));
        policyRepository.save(createPolicy("청년 창업 지원금", "창업 초기 사업화 자금 최대 1억원 지원", 19, 39, 4000000L, 10000000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("국민취업지원제도", "취업 활동 지원금 월 50만원 최대 6개월 지급", 15, 34, 2500000L, 3000000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("청년 고용 장려금", "중소기업 취업 청년 장려금 최대 200만원", 15, 34, 3000000L, 2000000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("청년 내일채움공제", "2년 근속 시 최대 1200만원 목돈 마련", 15, 34, 3600000L, 12000000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("소상공인 청년 창업 대출", "청년 창업자 운영자금 저금리 대출", 19, 39, 4000000L, 0L, "LOAN", "전국"));
        policyRepository.save(createPolicy("청년 신용회복 지원", "연체 청년 채무조정 및 신용회복 프로그램", 19, 34, 4000000L, 0L, "LOAN", "전국"));
        policyRepository.save(createPolicy("청년 우대형 청약통장", "일반 청약통장 대비 우대금리 및 비과세 혜택", 19, 34, 3600000L, 600000L, "SAVINGS", "전국"));
        policyRepository.save(createPolicy("청년 문화누리카드", "문화·여행·스포츠 이용권 연간 13만원 지원", 6, 34, 2000000L, 130000L, "SAVINGS", "전국"));
    }

    private Policy createPolicy(String name, String description, int minAge, int maxAge,
                                Long incomeLimit, Long benefitAmount, String policyType, String region) {
        return Policy.create(name, description, minAge, maxAge, incomeLimit, benefitAmount, policyType, region);
    }
}