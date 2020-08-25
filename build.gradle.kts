@file:Suppress("UNUSED_VARIABLE")

plugins {
	kotlin("multiplatform") version "1.4.0"
	id("maven-publish")
}

group = "org.kiot"
version = "1.0.6.2"

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
				api("com.github.KiotLand.kiot-binary:kiot-binary:1.0.4")
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
				api("com.github.KiotLand.kiot-binary:kiot-binary-js:1.0.4")
				api(npm("text-encoding", "0.7.0"))
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
				api("com.github.KiotLand.kiot-binary:kiot-binary-jvm:1.0.4")
				api("com.github.Mivik:Kot:1.0.4")
			}
		}
		val jvmTest by getting {
			dependencies {
				implementation(kotlin("test-junit"))
			}
		}
	}
}