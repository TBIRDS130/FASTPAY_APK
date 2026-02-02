// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Hilt and KSP are declared only in :app so both use the same class loader (see dagger#3965).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.gms.google.services) apply false
}
