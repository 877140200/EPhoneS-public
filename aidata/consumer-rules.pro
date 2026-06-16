# Consumer ProGuard rules for aidata module
# These rules will be applied to consuming modules

# Keep public API interfaces
-keep public interface com.susking.ephone_s.aidata.api.** { *; }
-keep public interface com.susking.ephone_s.aidata.domain.repository.** { *; }