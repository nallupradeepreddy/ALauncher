package com.alauncher.app.audit

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * High-level Play Console Pre-Launch & Policy Compliance Audit Suite.
 * Automates checks that mimic the Google Play Console pre-launch report and policy review.
 */
class PlayConsolePreLaunchAuditTest {

    private lateinit var manifestFile: File
    private lateinit var manifestDoc: org.w3c.dom.Document

    @Before
    fun setUp() {
        // Resolve AndroidManifest.xml from module dir or root
        manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() } ?: throw IllegalStateException("AndroidManifest.xml not found")

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        manifestDoc = builder.parse(manifestFile)
    }

    @Test
    fun auditZeroNetworkPermission_Ensures100PercentOffline() {
        // Google Play Data Safety & User Trust:
        // ALauncher guarantees 100% local, offline operation.
        // Assert that android.permission.INTERNET is NOT declared in the manifest.
        val permissions = manifestDoc.getElementsByTagName("uses-permission")
        for (i in 0 until permissions.length) {
            val element = permissions.item(i) as Element
            val name = element.getAttribute("android:name")
            assertNotEquals("INTERNET permission must NEVER be present in ALauncher", "android.permission.INTERNET", name)
            assertNotEquals("ACCESS_NETWORK_STATE must not be requested unnecessarily", "android.permission.ACCESS_NETWORK_STATE", name)
        }
    }

    @Test
    fun auditZeroContactsPermission_EnsuresCompleteQuickContactsRemovalAndPrivacy() {
        // Privacy & Minimal Privilege Guarantee:
        // Quick Contacts feature was completely removed. Assert that no contact-related permissions exist in the manifest.
        val permissions = manifestDoc.getElementsByTagName("uses-permission")
        for (i in 0 until permissions.length) {
            val element = permissions.item(i) as Element
            val name = element.getAttribute("android:name")
            assertNotEquals("READ_CONTACTS permission must NEVER be present in ALauncher", "android.permission.READ_CONTACTS", name)
            assertNotEquals("WRITE_CONTACTS permission must NEVER be present in ALauncher", "android.permission.WRITE_CONTACTS", name)
            assertNotEquals("GET_ACCOUNTS permission must NEVER be present in ALauncher", "android.permission.GET_ACCOUNTS", name)
        }

        // Also assert that <queries> does not query Contacts provider
        val queriesNodes = manifestDoc.getElementsByTagName("queries")
        for (i in 0 until queriesNodes.length) {
            val queriesElem = queriesNodes.item(i) as Element
            val intentNodes = queriesElem.getElementsByTagName("intent")
            for (j in 0 until intentNodes.length) {
                val intentElem = intentNodes.item(j) as Element
                val dataNodes = intentElem.getElementsByTagName("data")
                for (k in 0 until dataNodes.length) {
                    val dataElem = dataNodes.item(k) as Element
                    val mimeType = dataElem.getAttribute("android:mimeType")
                    assertFalse("Manifest queries must not reference contacts mimeType", mimeType.contains("contact"))
                }
            }
        }
    }

    @Test
    fun auditQueryAllPackagesJustification_MustDeclareHomeCategory() {
        // Google Play Restricted Permissions Policy:
        // Apps requesting QUERY_ALL_PACKAGES must qualify for an approved core use case.
        // Launchers qualify under the "Home Screen / Launcher" exemption.
        // Verify that the manifest declares an Activity with intent-filter category HOME and DEFAULT.
        val activities = manifestDoc.getElementsByTagName("activity")
        var hasHomeCategory = false

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val categories = activity.getElementsByTagName("category")
            for (j in 0 until categories.length) {
                val cat = categories.item(j) as Element
                if (cat.getAttribute("android:name") == "android.intent.category.HOME") {
                    hasHomeCategory = true
                }
            }
        }
        assertTrue("QUERY_ALL_PACKAGES requires an Activity with category.HOME to pass Play Console review", hasHomeCategory)
    }

    @Test
    fun auditComponentExportSecurity_AllComponentsMustExplicitlyDeclareExported() {
        // Android 12+ (API 31+) & Play Console Security Requirement:
        // Every activity, service, and receiver must explicitly specify android:exported.
        val componentTags = listOf("activity", "service", "receiver")

        for (tag in componentTags) {
            val nodeList = manifestDoc.getElementsByTagName(tag)
            for (i in 0 until nodeList.length) {
                val elem = nodeList.item(i) as Element
                val componentName = elem.getAttribute("android:name")
                val hasExportedAttr = elem.hasAttribute("android:exported")
                assertTrue(
                    "Component '$componentName' (<$tag>) must explicitly declare android:exported='true' or 'false'",
                    hasExportedAttr
                )
            }
        }
    }

    @Test
    fun auditForegroundServiceCompliance_Android14SpecialUseFgs() {
        // Android 14+ (API 34+) Google Play FGS Policy:
        // Any service with foregroundServiceType="specialUse" MUST:
        // 1. Declare FOREGROUND_SERVICE and FOREGROUND_SERVICE_SPECIAL_USE permissions.
        // 2. Include <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" /> with a valid description.
        val services = manifestDoc.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val serviceType = service.getAttribute("android:foregroundServiceType")
            if (serviceType == "specialUse") {
                val serviceName = service.getAttribute("android:name")
                val properties = service.getElementsByTagName("property")
                var hasSubtypeProperty = false
                var propertyValue = ""

                for (j in 0 until properties.length) {
                    val prop = properties.item(j) as Element
                    if (prop.getAttribute("android:name") == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE") {
                        hasSubtypeProperty = true
                        propertyValue = prop.getAttribute("android:value")
                    }
                }
                assertTrue(
                    "Service '$serviceName' with specialUse FGS must declare PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
                    hasSubtypeProperty
                )
                assertTrue(
                    "Service '$serviceName' PROPERTY_SPECIAL_USE_FGS_SUBTYPE value must not be blank",
                    propertyValue.isNotBlank()
                )
            }
        }
    }

    @Test
    fun auditNotificationListenerServiceSecurity() {
        // System Security:
        // NotificationListenerService must be protected by BIND_NOTIFICATION_LISTENER_SERVICE permission
        val services = manifestDoc.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val serviceName = service.getAttribute("android:name")
            if (serviceName.contains("NotificationListener")) {
                val permission = service.getAttribute("android:permission")
                assertEquals(
                    "NotificationListener must require android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                    permission
                )
            }
        }
    }

    @Test
    fun auditNoProhibitedHighRiskPermissions() {
        // Google Play High-Risk Permissions Policy:
        // Reject unnecessary high-risk SMS, Call Log, and Location permissions that lead to Play Store rejection.
        val prohibitedPermissions = setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS"
        )

        val permissions = manifestDoc.getElementsByTagName("uses-permission")
        for (i in 0 until permissions.length) {
            val element = permissions.item(i) as Element
            val name = element.getAttribute("android:name")
            assertFalse(
                "Prohibited high-risk permission '$name' must not be requested",
                prohibitedPermissions.contains(name)
            )
        }
    }

    @Test
    fun auditZeroContactPermissions_EnsuresComplianceWith2027ContactsPolicy() {
        // Google Play Contacts Permission Policy:
        // ALauncher must NEVER declare broad READ_CONTACTS or WRITE_CONTACTS in AndroidManifest.xml.
        val permissions = manifestDoc.getElementsByTagName("uses-permission")
        for (i in 0 until permissions.length) {
            val element = permissions.item(i) as Element
            val name = element.getAttribute("android:name")
            assertNotEquals("READ_CONTACTS must not be declared in manifest", "android.permission.READ_CONTACTS", name)
            assertNotEquals("WRITE_CONTACTS must not be declared in manifest", "android.permission.WRITE_CONTACTS", name)
        }
    }

    @Test
    fun auditDataExtractionAndBackupRulesXmlValid() {
        // Ensure data extraction rules and backup rules XML exist and are valid XML
        val resDir = manifestFile.parentFile!!.resolve("res/xml")
        val dataRules = resDir.resolve("data_extraction_rules.xml")
        val backupRules = resDir.resolve("backup_rules.xml")

        assertTrue("data_extraction_rules.xml must exist in res/xml", dataRules.exists())
        assertTrue("backup_rules.xml must exist in res/xml", backupRules.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        assertNotNull("data_extraction_rules.xml must be valid XML", builder.parse(dataRules))
        assertNotNull("backup_rules.xml must be valid XML", builder.parse(backupRules))
    }

    @Test
    fun auditAccessibilityServiceCompliance_WhenDeclared_RequiresStoreDisclosureAndInAppNotice() {
        // Google Play Accessibility API Policy:
        // Apps declaring BIND_ACCESSIBILITY_SERVICE must:
        // 1. Declare the service with android.permission.BIND_ACCESSIBILITY_SERVICE and exported="true"
        // 2. Point to a valid accessibility-service config XML with android:description
        // 3. Document explicit disclosure in the Play Store listing description (README.md)
        // 4. Provide prominent in-app disclosure before prompting for the permission
        val services = manifestDoc.getElementsByTagName("service")
        var accessibilityServiceName: String? = null
        var configXmlName: String? = null

        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            if (service.getAttribute("android:permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE") {
                accessibilityServiceName = service.getAttribute("android:name")
                assertEquals("AccessibilityService must declare android:exported='true' so the Android OS can bind to it", "true", service.getAttribute("android:exported"))

                val metaDataList = service.getElementsByTagName("meta-data")
                for (j in 0 until metaDataList.length) {
                    val meta = metaDataList.item(j) as Element
                    if (meta.getAttribute("android:name") == "android.accessibilityservice") {
                        configXmlName = meta.getAttribute("android:resource")
                    }
                }
            }
        }

        if (accessibilityServiceName != null) {
            // Verify config XML
            assertNotNull("AccessibilityService must specify @xml/accessibility_service_config meta-data", configXmlName)
            val resDir = manifestFile.parentFile!!.resolve("res")
            val configFile = resDir.resolve("xml/accessibility_service_config.xml")
            assertTrue("accessibility_service_config.xml must exist", configFile.exists())

            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val configDoc = builder.parse(configFile)
            val root = configDoc.documentElement
            assertEquals("Root tag of accessibility config must be accessibility-service", "accessibility-service", root.tagName)
            assertTrue("Config must declare android:description", root.hasAttribute("android:description"))

            // Verify Play Store listing disclosure in fastlane metadata or README.md
            val storeDescriptionFile = listOf(
                File("fastlane/metadata/android/en-US/full_description.txt"),
                File("../fastlane/metadata/android/en-US/full_description.txt"),
                manifestFile.parentFile?.parentFile?.parentFile?.resolve("fastlane/metadata/android/en-US/full_description.txt"),
                File("README.md"),
                File("../README.md"),
                manifestFile.parentFile?.parentFile?.parentFile?.resolve("README.md")
            ).filterNotNull().firstOrNull { it.exists() }

            assertNotNull("Play Store description file must exist in fastlane/metadata/android/en-US/full_description.txt or README.md", storeDescriptionFile)
            val storeDescriptionContent = storeDescriptionFile!!.readText()
            assertTrue(
                "Play Store description must contain Accessibility Service usage disclosure",
                storeDescriptionContent.contains("ACCESSIBILITY SERVICE USAGE") ||
                    storeDescriptionContent.contains("ACCESSIBILITY SERVICE API USAGE")
            )
            assertTrue(
                "Play Store description must explain the purpose (screen lock / double tap)",
                storeDescriptionContent.contains("GLOBAL_ACTION_LOCK_SCREEN") || storeDescriptionContent.contains("Double-Tap to Lock")
            )
            assertTrue(
                "Play Store description must explicitly guarantee zero data collection",
                storeDescriptionContent.contains("ALauncher does NOT collect, read, store, or transmit any personal data") ||
                    storeDescriptionContent.contains("does NOT collect")
            )

            // Verify strings.xml prominent in-app disclosure
            val stringsFile = resDir.resolve("values/strings.xml")
            assertTrue("strings.xml must exist", stringsFile.exists())
            val stringsText = stringsFile.readText()
            assertTrue(
                "strings.xml must contain prominent in-app disclosure dialog strings mentioning Accessibility Service API",
                stringsText.contains("Accessibility Service API") || stringsText.contains("Accessibility Service")
            )
            assertTrue(
                "strings.xml in-app disclosure must explicitly assure user that personal data is not collected",
                stringsText.contains("does not collect")
            )
        }
    }

    @Test
    fun auditAndroid15EdgeToEdgeCompliance_ActivitiesAndSourceCode() {
        // Android 15+ (SDK 35+) Edge-to-Edge Compliance:
        // 1. Every Activity must invoke enableEdgeToEdge() before super.onCreate().
        // 2. App source files must NEVER directly invoke deprecated Android 15 edge-to-edge APIs:
        //    - Window.setStatusBarColor / window.statusBarColor
        //    - Window.setNavigationBarColor / window.navigationBarColor
        //    - LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES / LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        val srcJavaDir = manifestFile.parentFile!!.resolve("java")
        assertTrue("src/main/java directory must exist", srcJavaDir.exists())

        val kotlinFiles = srcJavaDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Kotlin source files must exist", kotlinFiles.isNotEmpty())

        val forbiddenPatterns = listOf(
            "setStatusBarColor",
            "statusBarColor =",
            "setNavigationBarColor",
            "navigationBarColor =",
            "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES",
            "LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER"
        )

        for (ktFile in kotlinFiles) {
            val content = ktFile.readText()
            for (forbidden in forbiddenPatterns) {
                assertFalse(
                    "Deprecated Android 15 Edge-to-Edge API '$forbidden' found in ${ktFile.name}",
                    content.contains(forbidden)
                )
            }
        }

        // Verify MainActivity and ErrorActivity call enableEdgeToEdge before super.onCreate
        val activityFiles = listOf("MainActivity.kt", "ErrorActivity.kt")
        for (actName in activityFiles) {
            val actFile = kotlinFiles.firstOrNull { it.name == actName }
            assertNotNull("$actName must exist in source tree", actFile)
            val text = actFile!!.readText()
            val edgeIdx = text.indexOf("enableEdgeToEdge(")
            val superIdx = text.indexOf("super.onCreate(savedInstanceState)")
            assertTrue("$actName must call enableEdgeToEdge()", edgeIdx >= 0)
            assertTrue("$actName must call super.onCreate(savedInstanceState)", superIdx >= 0)
            assertTrue(
                "$actName must call enableEdgeToEdge() BEFORE super.onCreate(savedInstanceState)",
                edgeIdx < superIdx
            )
        }

        // Verify Predictive Back Gesture is enabled in AndroidManifest.xml
        val appElement = manifestDoc.getElementsByTagName("application").item(0) as Element
        assertEquals(
            "application must declare android:enableOnBackInvokedCallback='true' for Android 14/15+ Predictive Back Gesture",
            "true",
            appElement.getAttribute("android:enableOnBackInvokedCallback")
        )
    }
}
