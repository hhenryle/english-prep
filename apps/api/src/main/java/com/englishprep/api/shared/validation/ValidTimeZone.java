package com.englishprep.api.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The value must be an IANA time zone id such as "Asia/Ho_Chi_Minh". Null is allowed. */
@Documented
@Constraint(validatedBy = ValidTimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeZone {

    String message() default "must be a valid time zone id, for example Asia/Ho_Chi_Minh";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
