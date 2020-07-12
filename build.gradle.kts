@file:Suppress("UNUSED_VARIABLE")

plugins {
	kotlin("multiplatform") version "1.3.72"
	id("maven-publish")
}

group = "org.kiot"
version = "1.0.5.2"

repositories {
	mavenCentral()
	maven("https://jitpack.io")
}

dependencies {
}

kotlin {
	jvm {
		compilations.all {
			kotlinOptions {
				jvmTarget = "1.8"
			}
		}
	}

	js {
		browser {}
		nodejs {}
	}

	sourceSets {
		all {
			languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
		}
		val commonMain by getting {
			dependencies {
				implementation(kotlin("stdlib-common"))
				implementation("com.github.KiotLand.kiot-binary:kiot-binary:1.0.3")
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test-common"))
				implementation(kotlin("test-annotations-common"))
			}
		}
		val jsMain by getting {
			dependencies {
				implementation(kotlin("stdlib-js"))
				api("com.github.KiotLand.kiot-binary:kiot-binary-js:1.0.3")
				api(npm("text-encoding"))
			}
		}
		val jsTest by getting {
			dependencies {
				implementation(kotlin("test-js"))
			}
		}
		val jvmMain by getting {
			dependencies {
				implementation(kotlin("stdlib-jdk8"))
				implementation("com.github.KiotLand.kiot-binary:kiot-binary-jvm:1.0.3")
				implementation("com.github.Mivik:Kot:1.0.4")
			}
		}
		val jvmTest by getting {
			dependencies {
				implementation(kotlin("test-junit"))
			}
		}
	}
}