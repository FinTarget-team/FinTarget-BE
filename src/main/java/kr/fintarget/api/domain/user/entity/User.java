package kr.fintarget.api.domain.user.entity;

import jakarta.persistence.*;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import lombok.*;
import java.time.LocalDateTime;
import java.time.Year;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false, unique = true)
    private String providerId;

    private String name;
    private String email;
    private String region;

    // 출생연도 입력 도입 전(#87)에 저장된 연령대 대표값(25/40). birthYear가 없을 때만 getAge()의 폴백으로 쓴다
    private Integer age;
    private Integer birthYear;
    private Long income;
    private String employmentType;
    // HousingStatus 코드. 선택 안 함과 주택 단계 도입(#87) 전 미응답은 모두 null
    private String housingStatus;

    @Column(nullable = false)
    private boolean onboardingCompleted = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void updateProfile(String name, String email, String region) {
        if (name != null) this.name = name;
        if (email != null) this.email = email;
        if (region != null) this.region = region;
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    /**
     * 정책 매칭용 나이. birthYear가 있으면 올해(Asia/Seoul) 기준으로 계산하고, 없으면 저장된 age(25/40)를 반환한다.
     * 계산식은 (올해 - 출생연도 - 1), 즉 올해 생일이 아직 지나지 않았다고 가정한 만 나이다.
     * 월일을 받지 않으므로 올해 생일이 지난 사용자는 실제 만 나이보다 1살 적게 계산되며, 이 때문에
     * - 최소 나이 경계: 올해 최소 나이가 되는 사용자는 생일이 지나도 제외된다 (예: 19세 이상 정책, 올해 19세가 되는 사용자)
     * - 최대 나이 경계: 올해 최대 나이를 넘는 사용자는 생일이 지나도 통과된다 (예: 39세 이하 정책, 올해 40세가 되는 사용자)
     * 정책 나이 범위와 비교하는 방식은 #99에서 바꾼다.
     */
    public Integer getAge() {
        return ageIn(Year.now(BirthYearValidator.ZONE));
    }

    Integer ageIn(Year currentYear) {
        if (birthYear == null) {
            return age;
        }
        return currentYear.getValue() - birthYear - 1;
    }

    public void updateBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    public void updateIncome(Long income) {
        this.income = income;
    }

    public void updateEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public void updateHousingStatus(String housingStatus) {
        this.housingStatus = housingStatus;
    }

    public void updateRegion(String region) {
        this.region = region;
    }

    public void updateName(String name) {
        this.name = name;
    }
}