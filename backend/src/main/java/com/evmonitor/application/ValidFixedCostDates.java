package com.evmonitor.application;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidFixedCostDates.Validator.class)
@Documented
public @interface ValidFixedCostDates {
    String message() default "Invalid date combination for recurrence type";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidFixedCostDates, FixedCostRequest> {
        @Override
        public boolean isValid(FixedCostRequest r, ConstraintValidatorContext ctx) {
            if (r == null || r.recurrence() == null) return true;
            return switch (r.recurrence()) {
                case ONE_TIME -> r.date() != null;
                case MONTHLY, QUARTERLY, YEARLY -> r.startDate() != null;
            };
        }
    }
}
