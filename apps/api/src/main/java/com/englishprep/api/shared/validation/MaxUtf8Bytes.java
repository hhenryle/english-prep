package com.englishprep.api.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value, encoded as UTF-8, must not be longer than {@code value} bytes. Needed for passwords:
 * BCrypt only reads the first 72 bytes, and a character can take up to 4 bytes, so a length
 * limit in characters is not enough. Null is allowed.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "must be at most {value} bytes when encoded as UTF-8";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
