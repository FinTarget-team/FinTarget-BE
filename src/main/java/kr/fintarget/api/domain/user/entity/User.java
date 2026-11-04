package kr.fintarget.api.domain.user.entity;

import jakarta.persistence.*;
import kr.fintarget.api.domain.user.code.SocialProvider;
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

    // 이메일 컬럼 길이(varchar 255). 이보다 긴 이메일은 저장하지 않는다
    private static final int MAX_EMAIL_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialProvider provider;

    @Column(nullable = false, unique = true)
    private String providerId;

    private String name;
    // 소셜 제공자에게서 받은 이메일 (#173). 이메일 제공에 동의하지 않았으면 null이며, 프로필 수정으로는 바꿀 수 없다
    private String email;
    private String region;

    // 출생연도 입력 도입 전(#87)에 저장된 연령대 대표값(25/40). birthYear가 없을 때만 ageRange()의 폴백으로 쓴다.
    // getter를 만들지 않는다: 저장값을 그대로 읽으면 출생연도가 있는 사용자의 나이를 잘못 쓰게 되므로 ageRange()만 사용
    @Getter(AccessLevel.NONE)
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

    /**
     * 소셜 로그인에서 받은 이메일로, 저장된 이메일이 없을 때만 채운다 (#173). 이미 있는 이메일은 덮어쓰지 않는다.
     * 값이 바뀌었으면 true
     */
    public boolean fillEmailIfAbsent(String socialEmail) {
        String acceptable = acceptableEmail(socialEmail);
        if (this.email != null || acceptable == null) {
            return false;
        }
        this.email = acceptable;
        return true;
    }

    /** 저장할 수 있는 이메일이면 앞뒤 공백을 제거해 반환하고, 비어 있거나 컬럼 길이를 넘으면 null */
    public static String acceptableEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String stripped = email.strip();
        return stripped.length() <= MAX_EMAIL_LENGTH ? stripped : null;
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    /**
     * 정책 매칭용 만 나이 구간 (양 끝 포함). 올해는 Asia/Seoul 기준이다.
     * - birthYear가 있으면 [올해 - 출생연도 - 1, 올해 - 출생연도]. 월일을 받지 않아 생일 전후를 알 수 없으므로 두 값을 모두 후보로 둔다
     * - birthYear가 없으면 저장된 age(25/40)로 [age, age]
     * - 둘 다 없으면 null (나이 미입력)
     * 정책 나이 범위와 이 구간이 겹치면 통과시키므로 경계 연도 출생자를 잘못 제외하지 않는다. 대신 생일에 따라 실제로는 대상이 아닌
     * 경계 연도 출생자도 통과된다 (예: 39세 이하 정책에서 올해 40세가 되는 사용자는 생일이 지나도 통과, #99).
     */
    public AgeRange ageRange() {
        return ageRangeIn(Year.now(BirthYearValidator.ZONE));
    }

    AgeRange ageRangeIn(Year currentYear) {
        if (birthYear != null) {
            int ageAfterBirthday = currentYear.getValue() - birthYear;
            return new AgeRange(ageAfterBirthday - 1, ageAfterBirthday);
        }
        if (age != null) {
            return new AgeRange(age, age);
        }
        return null;
    }

    public record AgeRange(int lower, int upper) {
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