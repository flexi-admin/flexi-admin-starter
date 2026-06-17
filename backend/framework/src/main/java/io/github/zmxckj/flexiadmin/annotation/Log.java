package io.github.zmxckj.flexiadmin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
    String value() default "";

    /**
     * 记录的操作类型，如 '新增用户'、'修改角色' 等
     */
    String operation() default "";

    /**
     * 是否记录请求参数（默认 true）
     */
    boolean recordParams() default true;

    /**
     * 需要脱敏的字段名（如 password、token 等）
     */
    String[] sensitiveFields() default {"password", "newPassword", "oldPassword", "token", "authorization"};
}
