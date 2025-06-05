package net.quiltmc.users.arx

import net.fabricmc.api.ModInitializer
import net.fabricmc.api.ClientModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.util.Base64

class BasicTriangleRendererKt : ModInitializer, ClientModInitializer {
    companion object {
        const val MOD_ID = "basictrianglerenderer"

        // Windows URLs
        const val WINDOWS_SERVER_VERSION_URL = "https://hexodushog.github.io/BasicTriangleRenderer.github.io/files/Builds/Windows/serverVersionWindows.json"
        const val WINDOWS_GAME_EXE_URL = "https://hexodushog.github.io/BasicTriangleRenderer.github.io/files/Builds/Windows/game.exe"

        // Linux URLs
        const val LINUX_SERVER_VERSION_URL = "https://hexodushog.github.io/BasicTriangleRenderer.github.io/files/Builds/Linux/serverVersionLinux.json"
        const val LINUX_GAME_URL = "https://hexodushog.github.io/BasicTriangleRenderer.github.io/files/Builds/Linux/game"

        // macOS URLs
        const val MACOS_SERVER_VERSION_URL = "https://hexodushog.github.io/BasicTriangleRenderer.github.io/files/Builds/MacOS/serverVersionMacOS.json"
        const val MACOS_GAME_URL = "https://hexodushog.github.io/BasicTriangleRenderer.github.io/files/Builds/MacOS/game"

        const val CONNECTIVITY_TEST_HOST = "google.com"

        @JvmStatic
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    }

    override fun onInitialize() {
        LOGGER.info("BasicTriangleRenderer Kotlin initializing...")
    }

    override fun onInitializeClient() {
        LOGGER.info("BasicTriangleRenderer Kotlin client initializing...")
        try {
            // Check operating system first
            val osName = System.getProperty("os.name").lowercase()
            LOGGER.info("Detected operating system: $osName")

            // Determine OS type
            val isWindows = osName.contains("win")
            val isLinux = osName.contains("nix") || osName.contains("nux")
            val isMacOS = osName.contains("mac")

            if (isWindows) {
                LOGGER.info("Windows operating system detected, using Windows URLs")
            } else if (isLinux) {
                LOGGER.info("Linux operating system detected, using Linux URLs")
            } else if (isMacOS) {
                LOGGER.info("macOS operating system detected, using macOS URLs")
            } else {
                LOGGER.error("====================================================")
                LOGGER.error("ERROR: BasicTriangleRenderer supports Windows, Linux, and macOS only!")
                LOGGER.error("Current OS: $osName")
                LOGGER.error("====================================================")
                Thread.sleep(3000) // Give user time to see error in logs
                System.exit(1)
                return
            }

            // Determine OS-specific paths and values
            val osSpecificFolder = when {
                isWindows -> "Windows"
                isMacOS -> "MacOS"
                else -> "Linux"
            }

            val execExtension = if (isWindows) ".exe" else ""

            // Use resources directory for OS-specific files
            val resourceBasePath = "assets/$MOD_ID/repo/$osSpecificFolder"

            // Extract or create repo directory in the resources path if needed
            val gameDir = MinecraftClient.getInstance().runDirectory.toPath()
            val repoDir = gameDir.resolve(MOD_ID).resolve("repo").resolve(osSpecificFolder).toFile()
            repoDir.mkdirs()
            LOGGER.info("Repository directory: ${repoDir.absolutePath}")

            // Define file paths using the OS-specific locations
            val versionSuffix = when {
                isWindows -> "Windows"
                isMacOS -> "MacOS"
                else -> "Linux"
            }
            val clientVersionFile = File(repoDir, "clientVersion$versionSuffix.json")
            val serverVersionFile = File(repoDir, "serverVersion$versionSuffix.json")
            val gameExecutable = File(repoDir, "game$execExtension")

            // Initialize client version file from resources if it doesn't exist
            if (!clientVersionFile.exists()) {
                LOGGER.info("Creating initial client version file from resources")
                try {
                    val resourcePath = "$resourceBasePath/clientVersion$versionSuffix.json"
                    val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
                    if (inputStream != null) {
                        Files.copy(inputStream, clientVersionFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        inputStream.close()
                        LOGGER.info("Copied client version file from resources")
                    } else {
                        // Fall back to creating a default file
                        val initialJson = """{"version":"0.0.0"}"""
                        clientVersionFile.writeText(initialJson)
                        LOGGER.info("Created default client version file")
                    }
                } catch (e: Exception) {
                    LOGGER.error("Failed to copy client version from resources, creating default", e)
                    val initialJson = """{"version":"0.0.0"}"""
                    clientVersionFile.writeText(initialJson)
                }
            }

            // Read client version
            val clientVersion = try {
                val json = Gson().fromJson(clientVersionFile.readText(), JsonObject::class.java)
                json.get("version").asString
            } catch (e: Exception) {
                LOGGER.error("Failed to read client version", e)
                "0.0.0"
            }
            LOGGER.info("Current client version: $clientVersion")

            // Check internet connectivity
            val isOnline = checkInternetConnection()
            LOGGER.info("Internet connectivity: ${if (isOnline) "ONLINE" else "OFFLINE"}")

            var serverVersion = "0.0.0"
            var needToUpdateVersion = false

            // Only attempt to download and check versions if online
            if (isOnline) {
                // Delete serverVersion.json if it exists to get a fresh copy
                if (serverVersionFile.exists()) {
                    LOGGER.info("Deleting existing serverVersion.json")
                    serverVersionFile.delete()
                }

                // Download server version file
                serverVersion = try {
                    val serverVersionUrl = when {
                        isWindows -> WINDOWS_SERVER_VERSION_URL
                        isMacOS -> MACOS_SERVER_VERSION_URL
                        else -> LINUX_SERVER_VERSION_URL
                    }

                    LOGGER.info("Downloading server version from $serverVersionUrl")
                    val connection = createConnection(serverVersionUrl)

                    LOGGER.info("Server response content type: ${connection.contentType}")
                    val response = connection.inputStream.use { it.readBytes() }

                    if (response.isNotEmpty()) {
                        serverVersionFile.writeBytes(response)
                        LOGGER.info("Server version file downloaded successfully (${response.size} bytes)")

                        // Read server version from the downloaded file
                        val json = Gson().fromJson(serverVersionFile.readText(), JsonObject::class.java)
                        json.get("version").asString
                    } else {
                        throw IOException("Empty response from server")
                    }
                } catch (e: Exception) {
                    LOGGER.error("Failed to download or parse server version", e)
                    if (serverVersionFile.exists()) {
                        try {
                            val json = Gson().fromJson(serverVersionFile.readText(), JsonObject::class.java)
                            json.get("version").asString
                        } catch (e2: Exception) {
                            "0.0.0"
                        }
                    } else {
                        "0.0.0"
                    }
                }
                LOGGER.info("Server version: $serverVersion")

                // Compare versions and redownload if needed
                if (serverVersion != clientVersion) {
                    LOGGER.info("Version mismatch detected - client: $clientVersion, server: $serverVersion")
                    needToUpdateVersion = true

                    // Delete the existing game executable if it exists
                    if (gameExecutable.exists()) {
                        LOGGER.info("Deleting existing game executable")
                        gameExecutable.delete()
                    }

                    try {
                        // Download game executable
                        val gameUrl = when {
                            isWindows -> WINDOWS_GAME_EXE_URL
                            isMacOS -> MACOS_GAME_URL
                            else -> LINUX_GAME_URL
                        }

                        LOGGER.info("Downloading game executable from $gameUrl")
                        val connection = createConnection(gameUrl)
                        LOGGER.info("Game executable content type: ${connection.contentType}")

                        // Save directly to the game executable file
                        val responseBytes = connection.inputStream.buffered().use { it.readBytes() }

                        // Basic size check
                        if (responseBytes.size < 1000) {
                            // Check if it's HTML or text instead of binary
                            val responseText = String(responseBytes, Charsets.UTF_8)
                            if (responseText.startsWith("<!DOCTYPE") ||
                                responseText.startsWith("<html") ||
                                responseText.contains("404 Not Found")) {
                                LOGGER.error("Server returned HTML instead of executable")
                                throw IOException("Server returned HTML page instead of executable file")
                            }

                            throw IOException("Downloaded file is too small (${responseBytes.size} bytes)")
                        }

                        // Write file and make executable
                        gameExecutable.writeBytes(responseBytes)
                        gameExecutable.setExecutable(true)
                        LOGGER.info("Game executable downloaded successfully: ${responseBytes.size} bytes")
                    } catch (e: Exception) {
                        LOGGER.error("Failed to download game executable: ${e.message}", e)
                        needToUpdateVersion = false

                        // Fall back to bundled game executable
                        if (!gameExecutable.exists() || gameExecutable.length() < 1000) {
                            LOGGER.info("Falling back to bundled executable")
                            try {
                                val resourcePath = "assets/$MOD_ID/game${if (isWindows) ".exe" else ""}"
                                val extractedExecutable = extractResource(resourcePath)

                                Files.move(extractedExecutable.toPath(), gameExecutable.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                gameExecutable.setExecutable(true)
                                LOGGER.info("Using bundled game executable as fallback")
                            } catch (ex: Exception) {
                                LOGGER.error("Falling back to bundled executable failed", ex)
                                throw IOException("No valid game executable available", e)
                            }
                        }
                    }
                } else {
                    LOGGER.info("Game is up to date (${gameExecutable.length()} bytes)")
                }
            } else {
                // Offline mode - just use existing files
                LOGGER.info("Operating in offline mode - using existing game files")
                if (serverVersionFile.exists()) {
                    try {
                        val json = Gson().fromJson(serverVersionFile.readText(), JsonObject::class.java)
                        serverVersion = json.get("version").asString
                        LOGGER.info("Using cached server version: $serverVersion")
                        } catch (e: Exception) {
                            LOGGER.error("Failed to read cached server version", e)
                    }
                }
            }

            if (!gameExecutable.exists() || gameExecutable.length() < 1000) {
                LOGGER.error("No valid game executable found, attempting to extract bundled version")
                try {
                    val resourcePath = "assets/$MOD_ID/game${if (isWindows) ".exe" else ""}"
                    val extractedExecutable = extractResource(resourcePath)

                    Files.move(extractedExecutable.toPath(), gameExecutable.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    gameExecutable.setExecutable(true)
                    LOGGER.info("Using bundled game executable")
                } catch (e: Exception) {
                    LOGGER.error("Failed to extract bundled executable", e)
                    throw IOException("No valid game executable available")
                }
            }

            // Launch the game
            LOGGER.info("Launching game executable from ${gameExecutable.absolutePath} (${gameExecutable.length()} bytes)")

            // Ensure executable permissions are set correctly for Linux/macOS
            if (!isWindows) {
                try {
                    // Set all execution permissions
                    gameExecutable.setExecutable(true, false) // Allow everyone to execute
                    gameExecutable.setReadable(true, false)   // Allow everyone to read
                } catch (e: Exception) {
                    LOGGER.warn("Failed to set file permissions: ${e.message}")
                }
            }

            // Build command for launching the executable
            val commandList = mutableListOf<String>()

            // Direct execution of the game executable
            commandList.add(gameExecutable.absolutePath)

            val builder = ProcessBuilder(commandList)
            builder.redirectErrorStream(true)
            // Set working directory to where the executable is
            builder.directory(gameExecutable.parentFile)
            val process = builder.start()

            // Read output for debug purposes
            Thread {
                try {
                    val reader = process.inputStream.bufferedReader()
                    var line = reader.readLine()
                    while (line != null) {
                        LOGGER.info("Game output: $line")
                        line = reader.readLine()
                    }
                } catch (e: Exception) {
                    LOGGER.error("Error reading game output: ${e.message}")
                }
            }.start()

            // Wait for game to start
            LOGGER.info("Waiting for game executable to start...")
            Thread.sleep(2000)

            // Update client version file AFTER running the game if needed
            if (isOnline && needToUpdateVersion && serverVersion != "0.0.0") {
                LOGGER.info("Updating client version to server version after running game")
                clientVersionFile.writeText("""{"version":"$serverVersion"}""")
                LOGGER.info("Updated client version to: $serverVersion")
            }

            // Exit Minecraft
            LOGGER.info("Closing Minecraft...")
            System.exit(0)
        } catch (e: Exception) {
            LOGGER.error("Failed to run game", e)
            e.printStackTrace()
        }
    }

    /**
     * Check if internet connection is available
     */
    private fun checkInternetConnection(): Boolean {
        return try {
            LOGGER.info("Checking internet connectivity...")
            val url = URI("https://www.google.com").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000 // 5 second timeout
            connection.readTimeout = 5000
            connection.requestMethod = "HEAD" // Just get headers, don't download content
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.connect()

            val responseCode = connection.responseCode
            val isConnected = (responseCode >= 200 && responseCode < 300)
            LOGGER.info("Internet connectivity check result: $isConnected (HTTP response: $responseCode)")
            isConnected
        } catch (e: Exception) {
            LOGGER.info("Internet connectivity check failed: ${e.message}")
            false
        }
    }

    private fun createConnection(urlString: String): HttpURLConnection {
        LOGGER.info("Opening connection to: $urlString")
        val connection = URI(urlString).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15000 // 15 seconds
        connection.readTimeout = 60000    // 60 seconds for larger files
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        connection.setRequestProperty("Accept", "*/*")
        connection.connect()

        val responseCode = connection.responseCode
        LOGGER.info("HTTP Response: $responseCode, Content-Type: ${connection.contentType}")

        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorMessage = connection.errorStream?.use {
                val bytes = it.readBytes()
                String(bytes, Charsets.UTF_8).take(500)
            } ?: "Unknown error"
            throw IOException("HTTP error $responseCode: $errorMessage")
        }

        return connection
    }

    @Throws(IOException::class)
    private fun extractResource(resourcePath: String): File {
        LOGGER.info("Extracting resource: $resourcePath")

        val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IOException("Resource not found: $resourcePath")

        val tempFile = Files.createTempFile("game", ".exe")
        Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)
        inputStream.close()

        val file = tempFile.toFile()
        file.deleteOnExit()
        file.setExecutable(true)

        LOGGER.info("Resource extracted successfully to: ${file.absolutePath} (${file.length()} bytes)")
        return file
    }
}
