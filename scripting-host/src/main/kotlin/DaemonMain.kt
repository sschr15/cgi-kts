package com.sschr15.scripting

import com.sschr15.scripting.api.HttpStatusCode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ServerSocketChannel
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.attribute.UserPrincipalNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.io.path.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalPathApi::class, ExperimentalStdlibApi::class)
object DaemonMain {
    private val logger = KotlinLogging.logger {}
    private val scriptCache = ScriptCache()

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val fileMap = ConcurrentHashMap<String, Path>()

        val checker = launch {
            val checkPath = Path(args.getOrNull(1) ?: "./kt-scripts")

            for (path in checkPath.listDirectoryEntries()) {
                if (path.toString().endsWith(".cgi.kts")) {
                    logger.info { "Compiling $path (first run)" }
                    fileMap[path.generateShortName()] = path
                    launch {
                        scriptCache.compile(path)
                    }
                }
            }

            val compileWatchKey = checkPath.register(
                FileSystems.getDefault().newWatchService(),
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
            )
            val removeWatchKey = checkPath.register(
                FileSystems.getDefault().newWatchService(),
                StandardWatchEventKinds.ENTRY_DELETE,
            )

            while (true) {
                for (event in compileWatchKey.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue

                    val path = checkPath.resolve(event.context() as Path)
                    if (!path.toString().endsWith(".cgi.kts")) continue

                    logger.info { "Detected change in $path" }

                    fileMap[path.generateShortName()] = path
                    launch {
                        scriptCache.compile(path)
                    }
                }

                for (event in removeWatchKey.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue

                    val path = checkPath.resolve(event.context() as Path)
                    if (!path.toString().endsWith(".cgi.kts")) continue

                    logger.info { "Detected removal of $path" }

                    fileMap.remove(path.generateShortName())
                    launch {
                        scriptCache.uncache(path)
                    }
                }

                delay(5.seconds)
            }
        }

        checker.start()

        val socket = Path(args.firstOrNull() ?: "~/cgi.sock")

        Runtime.getRuntime().addShutdownHook(Thread {
            socket.deleteIfExists()
        })

        val addr = UnixDomainSocketAddress.of(socket)

        val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(addr)

        val lookup = FileSystems.getDefault().userPrincipalLookupService
        val fcgiwrap = try {
            lookup.lookupPrincipalByName("fcgiwrap")
        } catch (_: UserPrincipalNotFoundException) {
            null
        }

        if (fcgiwrap != null) {
            // AclFileAttributeView isn't supported on Linux for some reason
//            val aclAttrib = socket.fileAttributesView<AclFileAttributeView>()
//            val permissions = aclAttrib.acl.toMutableList()
//            permissions.removeIf { it.principal() == fcgiwrap }
//            permissions.add(AclEntry.newBuilder().apply {
//                setPrincipal(fcgiwrap)
//                setType(AclEntryType.ALLOW)
//                setPermissions(
//                    AclEntryPermission.READ_DATA,
//                    AclEntryPermission.WRITE_DATA,
//                    AclEntryPermission.EXECUTE,
//                )
//            }.build())

            Runtime.getRuntime().exec(arrayOf(
                "setfacl",
                "-m",
                "u:$fcgiwrap:rwx",
                socket.absolutePathString()
            )).waitFor()
        }

        val runner = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        withContext(runner) {
            while (true) {
                logger.info { "Waiting for connection..." }
                channel.accept().use { client ->
                    val one = ByteBuffer.wrap(byteArrayOf(1))
                    val zero = ByteBuffer.wrap(byteArrayOf(0))
                    val lengthBuf = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)

                    fun respond(message: ByteArray) {
                        one.rewind()
                        zero.rewind()
                        val length = message.size
                        lengthBuf.rewind().putInt(length).rewind()
                        client.write(arrayOf(one, lengthBuf, ByteBuffer.wrap(message), zero))
                    }

                    fun respond(message: String) = respond(message.encodeToByteArray())

                    val scriptBytes = ByteBuffer.allocate(17)
                    client.read(scriptBytes)

                    val scriptName = scriptBytes.array()
                        .dropLastWhile { it.toInt() == 0 }
                        .toByteArray()
                        .decodeToString()

                    val scriptPath = fileMap[scriptName] ?: run {
                        logger.info { "No such file: $scriptName" }
                        respond("HTTP/1.1 ${HttpStatusCode.ClientError.NotFound}\r\n\r\n")
                        return@use
                    }

                    if (scriptPath !in scriptCache) {
                        // There was an error compiling, not the end user's result
                        logger.info { "Compilation failure: $scriptPath" }
                        respond(
                            """
                            |HTTP/1.1 ${HttpStatusCode.ServerError.InternalServerError}
                            |
                            |This script failed to compile.
                        """.trimMargin().replace("\n", "\r\n")
                        )
                        return@use
                    }

                    val amountToReadBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)

                    val result = scriptCache.evaluate(scriptPath) { env ->
                        logger.info { "Requesting $env" }

                        amountToReadBuffer.rewind()

                        zero.rewind()
                        client.write(arrayOf(zero, ByteBuffer.wrap(env.encodeToByteArray()), zero))
                        client.read(amountToReadBuffer)
                        amountToReadBuffer.rewind()
                        val bytesToRead = amountToReadBuffer.int

                        if (bytesToRead < 0) return@evaluate null

                        val buf = ByteBuffer.allocate(bytesToRead)
                        client.read(buf)

                        buf.array()
                            .dropLastWhile { it.toInt() == 0 }
                            .toByteArray()
                            .decodeToString()
                    }

                    logger.info { "Responding (${result.size} bytes)" }
                    respond(result)
                }
            }
        }
    }
}

private fun Path.generateShortName(): String {
    val baseFileName = name.removeSuffix(".cgi.kts")
    val shortName = if (baseFileName.length < 16) {
        baseFileName
    } else {
        // Create a 16-byte name from the first 9 characters and the last 6 characters
        // (and a one byte to "guarantee" distinctness)
        val firstPart = baseFileName.encodeToByteArray().take(9).toByteArray().decodeToString()
        val nameHash = baseFileName.hashCode()
        val hashString = nameHash.toString(36)
            .padStart(6, '0') // make at least 6 characters long
            .takeLast(6) // make at most 6 characters long
        "$firstPart\u0001$hashString"
    }
    return shortName
}
