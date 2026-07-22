package kr.fintarget.api.domain.simulation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private SimulationRequest requestWithRate(Double rate) {
        return new SimulationRequest(UUID.randomUUID(), 100_000L, null, rate);
    }

    @Test
    void 연이율이_null이면_유효하다() {
        assertThat(validator.validate(requestWithRate(null))).isEmpty();
    }

    @Test
    void 연이율이_0에서_1사이면_유효하다() {
        assertThat(validator.validate(requestWithRate(0.03))).isEmpty();
        assertThat(validator.validate(requestWithRate(1.0))).isEmpty();
        assertThat(validator.validate(requestWithRate(0.0))).isEmpty();
    }

    @Test
    void 연이율이_음수면_무효하다() {
        Set<ConstraintViolation<SimulationRequest>> violations = validator.validate(requestWithRate(-0.01));
        assertThat(violations).isNotEmpty();
    }

    @Test
    void 연이율이_1_초과면_무효하다() {
        Set<ConstraintViolation<SimulationRequest>> violations = validator.validate(requestWithRate(1.5));
        assertThat(violations).isNotEmpty();
    }

    @Test
    void 연이율이_무한대면_무효하다() {
        Set<ConstraintViolation<SimulationRequest>> violations = validator.validate(requestWithRate(Double.POSITIVE_INFINITY));
        assertThat(violations).isNotEmpty();
    }
}
