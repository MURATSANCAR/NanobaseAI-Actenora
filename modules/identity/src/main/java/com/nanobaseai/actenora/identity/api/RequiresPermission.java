package com.nanobaseai.actenora.identity.api;

import com.nanobaseai.actenora.identity.domain.Permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission required to invoke a controller method.
 * Enforced by {@code RequiresPermissionAspect} / authorization interceptor.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {
    Permission value();
}
