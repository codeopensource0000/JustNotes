// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
}

// Static analysis for Kotlin. The compiler checks that the code is correct and
// Android Lint that it is correct for Android; detekt looks at whether it is
// healthy — long functions, deep nesting, `!!`, over-broad catches, dead code.
//
// The build fails on a *new* finding, not on the existing ones. That is what
// the baseline file is for: the six findings detekt has today — all of them
// the known structural debt, all of them in the roadmap — are recorded there
// and stay silent, while anything introduced from now on breaks the build.
//
// The alternative, ignoreFailures = true, produces a report nobody opens. The
// alternative to *that*, failing on everything from day one, produces a
// morning spent suppressing rules. A baseline means the debt stops growing
// without anyone having to pay it off first.
//
// Removing a line from config/detekt-baseline.xml is how a piece of that debt
// gets retired deliberately.
detekt {
    source.setFrom(files("app/src/main/java", "app/src/test/java", "app/src/androidTest/java"))
    config.setFrom(files("config/detekt.yml"))
    baseline = file("config/detekt-baseline.xml")
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}
