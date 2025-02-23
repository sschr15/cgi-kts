package com.sschr15.scripting

import com.sschr15.scripting.api.HttpStatusCode
import kotlinx.coroutines.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ServerSocketChannel
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.attribute.UserPrincipalNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.io.path.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalPathApi::class)
object DaemonMain {
    private val scriptCache = ScriptCache()

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val fileMap = ConcurrentHashMap<String, Path>()

        val checker = launch {
            val checkPath = Path(args.getOrNull(1) ?: "./kt-scripts")

            while (true) {
                val needsCompilation = mutableListOf<Path>()
                val needsRemoval = mutableListOf<Path>()

                for ((key, value) in fileMap) {
                    if (!value.exists()) {
                        fileMap.remove(key)
                        needsRemoval.add(value)
                    }
                }

                checkPath.walk().forEach {
                    val baseFileName = it.name.removeSuffix(".cgi.kts")
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

                    if (fileMap[shortName] != it) {
                        fileMap[shortName] = it
                        needsCompilation.add(it)
                    }
                }

                for (path in needsCompilation) {
                    launch {
                        println("Found path: $path")
                        scriptCache.compile(path)
                    }
                }

                for (path in needsRemoval) {
                    launch {
                        println("Nonexistent path: $path")
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
                println("Waiting for connection...")
                channel.accept().use { client ->
                    val one = ByteBuffer.wrap(byteArrayOf(1))
                    val zero = ByteBuffer.wrap(byteArrayOf(0))

                    fun respond(message: String) {
                        val messageBytes = message.encodeToByteArray()
                        one.rewind()
                        zero.rewind()
                        client.write(arrayOf(one, ByteBuffer.wrap(messageBytes), zero))
                    }

                    val pidBytes = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
                    val scriptBytes = ByteBuffer.allocate(17)
                    client.read(arrayOf(pidBytes, scriptBytes))
                    pidBytes.rewind()

                    val pid = pidBytes.int
                    println("Communicating with PID $pid")

                    val scriptName = scriptBytes.array()
                        .dropLastWhile { it.toInt() == 0 }
                        .toByteArray()
                        .decodeToString()

                    val scriptPath = fileMap[scriptName] ?: run {
                        println("No such file: $scriptName")
                        respond("HTTP/1.1 ${HttpStatusCode.ClientError.NotFound}\r\n\r\n")
                        return@use
                    }

                    if (scriptPath !in scriptCache) {
                        // There was an error compiling, not the end user's result
                        println("Compilation failure: $scriptPath")
                        respond("""
                            |HTTP/1.1 ${HttpStatusCode.ServerError.InternalServerError}
                            |
                            |This script failed to compile.
                        """.trimMargin().replace("\n", "\r\n"))
                        return@use
                    }

                    val amountToReadBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)

                    val result = scriptCache.evaluate(scriptPath) { env ->
                        println("Requesting $env")

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

                    println("Responding (${result.length} characters)")
                    respond(result)
                }
            }
        }
    }
}
