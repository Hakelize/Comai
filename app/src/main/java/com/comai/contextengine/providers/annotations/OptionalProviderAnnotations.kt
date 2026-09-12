package com.comai.contextengine.providers.annotations

/**
 * Marks a context provider or model as an optional, non-MVP extension point.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class OptionalProvider(val note: String = "OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED")

/**
 * Marks a provider as requiring specific Android system or runtime permissions.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.PROPERTY)
annotation class PermissionDependent(val requiredPermission: String = "")

/**
 * Marks a provider as restricted by Android OS capabilities or security boundaries.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.PROPERTY)
annotation class AndroidRestricted(val restrictionDetails: String = "")
